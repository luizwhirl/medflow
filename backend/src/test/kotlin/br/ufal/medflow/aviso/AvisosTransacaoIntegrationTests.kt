package br.ufal.medflow.aviso

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.integracao.FonteEstoque
import br.ufal.medflow.integracao.Geocodificador
import br.ufal.medflow.integracao.LimitesMunicipais
import br.ufal.medflow.integracao.PosicaoEstoque
import br.ufal.medflow.integracao.QUALQUER_LUGAR
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.sincronizacao.SincronizacaoEstoqueService
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Testcontainers

/** Verifica a atomicidade do estoque e dos avisos no PostgreSQL. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class AvisosTransacaoIntegrationTests(
    @Autowired private val sincronizacao: SincronizacaoEstoqueService,
    @Autowired private val unidades: UbsRepository,
    @Autowired private val medicamentos: MedicamentoRepository,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val transactionManager: PlatformTransactionManager,
) {

    @MockitoBean
    private lateinit var fonte: FonteEstoque

    /** Sem chamar o IBGE: aceita qualquer coordenada, a não ser que o teste troque. */
    @MockitoBean
    private lateinit var limites: LimitesMunicipais

    /** Sem chamar o OpenStreetMap: sem bairro achado, vale só o contorno do município. */
    @MockitoBean
    private lateinit var geocodificador: Geocodificador

    @BeforeEach
    fun contornoQueAceitaTudo() {
        given(limites.limite(anyString())).willReturn(QUALQUER_LUGAR)
    }

    private val ibge = "2702405"

    private fun quantidadeDeAvisos(dispositivoId: UUID): Long =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM aviso WHERE dispositivo_id = ?",
            Long::class.java,
            dispositivoId,
        ) ?: 0L

    private fun quantidadeDeItens(ubsId: Long): Long =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_estoque WHERE ubs_id = ?",
            Long::class.java,
            ubsId,
        ) ?: 0L

    @Test
    fun `rollback da transacao reverte estoque e avisos juntos`() {
        val dispositivoId = UUID.randomUUID()

        val ubs = unidades.saveAndFlush(
            Ubs(
                cnes = "9876543",
                nome = "UBS Teste de Transação",
                tipo = TipoUnidade.UBS,
                bairro = "Centro",
                endereco = "Rua A, 1",
                codigoIbgeMunicipio = ibge,
                latitude = -9.387756,
                longitude = -38.000369,
            ),
        )

        val medicamento = medicamentos.saveAndFlush(
            Medicamento(
                catmat = "BR0267691",
                nome = "Metformina",
                concentracao = "850 mg",
                formaFarmaceutica = "comprimido",
                categoria = "Diabetes",
                rename = false,
            ),
        )

        val ubsId = requireNotNull(ubs.id)
        val medicamentoId = requireNotNull(medicamento.id)

        try {
            jdbc.update(
                """
                INSERT INTO item_lista_desejo (dispositivo_id, ubs_id, medicamento_id, notificar)
                VALUES (?, ?, ?, TRUE)
                """.trimIndent(),
                dispositivoId, ubsId, medicamentoId,
            )

            given(fonte.buscarPosicoes(ibge, null))
                .willReturn(
                    listOf(
                        PosicaoEstoque(
                            codigoIbgeMunicipio = ibge,
                            cnes = ubs.cnes,
                            nomeUnidade = "UBS TESTE DE TRANSACAO",
                            logradouro = "RUA A",
                            numero = "1",
                            bairro = "CENTRO",
                            cep = "57480000",
                            telefone = null,
                            latitude = -9.387756,
                            longitude = -38.000369,
                            catmat = "BR0267691U0042",
                            descricaoProduto =
                                "METFORMINA, CLORIDRATO 850 MG COMPRIMIDO",
                            quantidade = BigDecimal("100"),
                            numeroLote = "L1",
                            validade = LocalDate.of(2027, 10, 31),
                            dataPosicao = LocalDate.of(2026, 11, 19),
                        ),
                    ),
                )

            assertEquals(0L, quantidadeDeItens(ubsId))
            assertEquals(0L, quantidadeDeAvisos(dispositivoId))

            // A sincronização participa da transação externa.
            // Simulamos uma falha depois das gravações, mas antes
            // da confirmação da transação.
            assertFailsWith<IllegalStateException> {
                TransactionTemplate(transactionManager)
                    .executeWithoutResult {
                        sincronizacao.sincronizar(ibge)

                        // Confirma que ambos os registros foram
                        // criados dentro da transação.
                        assertEquals(
                            1L,
                            quantidadeDeItens(ubsId),
                        )
                        assertEquals(
                            1L,
                            quantidadeDeAvisos(dispositivoId),
                        )

                        throw IllegalStateException(
                            "Falha simulada antes do commit",
                        )
                    }
            }

            // Depois do rollback, nenhum dos dois registros
            // pode permanecer no banco.
            assertEquals(
                0L,
                quantidadeDeItens(ubsId),
            )
            assertEquals(
                0L,
                quantidadeDeAvisos(dispositivoId),
            )
        } finally {
            // Limpa apenas os dados criados por este teste.
            jdbc.update(
                "DELETE FROM aviso WHERE dispositivo_id = ?",
                dispositivoId,
            )
            jdbc.update(
                "DELETE FROM item_lista_desejo WHERE dispositivo_id = ?",
                dispositivoId,
            )
            jdbc.update(
                """
                DELETE FROM lote_estoque
                WHERE item_estoque_id IN (
                    SELECT id FROM item_estoque WHERE ubs_id = ?
                )
                """.trimIndent(),
                ubsId,
            )
            jdbc.update(
                "DELETE FROM item_estoque WHERE ubs_id = ?",
                ubsId,
            )
            jdbc.update(
                "DELETE FROM ubs WHERE id = ?",
                ubsId,
            )
            jdbc.update(
                "DELETE FROM medicamento WHERE id = ?",
                medicamentoId,
            )
        }
    }
}
