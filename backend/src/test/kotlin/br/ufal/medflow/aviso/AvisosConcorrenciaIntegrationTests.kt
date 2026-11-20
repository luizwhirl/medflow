package br.ufal.medflow.aviso

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import br.ufal.medflow.integracao.FonteEstoque
import br.ufal.medflow.integracao.PosicaoEstoque
import br.ufal.medflow.listadesejo.ItemListaDesejo
import br.ufal.medflow.listadesejo.ItemListaDesejoRepository
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.sincronizacao.SincronizacaoEstoqueService
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testa a proteção contra sincronizações simultâneas
 * do mesmo município usando PostgreSQL real.
 *
 * Não utiliza @Transactional na classe, pois cada thread
 * precisa executar sua própria transação independente.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class AvisosConcorrenciaIntegrationTests(
    @Autowired private val sincronizacao: SincronizacaoEstoqueService,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val unidades: UbsRepository,
    @Autowired private val medicamentos: MedicamentoRepository,
    @Autowired private val listaDesejos: ItemListaDesejoRepository,
    @Autowired private val avisos: AvisoRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @MockitoBean
    private lateinit var fonte: FonteEstoque

    private val ibge = "2702405"

    private fun posicao(cnes: String): PosicaoEstoque =
        PosicaoEstoque(
            codigoIbgeMunicipio = ibge,
            cnes = cnes,
            nomeUnidade = "UBS TESTE CONCORRENCIA",
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
        )

    @Test
    fun `sincronizacoes simultaneas geram somente um aviso`() {
        val identificador = UUID.randomUUID().toString()

        val usuario = usuarios.saveAndFlush(
            Usuario(
                nome = "Cidadão Concorrência",
                email = "concorrencia-$identificador@teste.com",
                senhaHash = "hash-teste",
                papel = PapelUsuario.CIDADAO,
            ),
        )

        val ubs = unidades.saveAndFlush(
            Ubs(
                cnes = "9876542",
                nome = "UBS Teste Concorrencia",
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

        val usuarioId = requireNotNull(usuario.id)
        val ubsId = requireNotNull(ubs.id)
        val medicamentoId = requireNotNull(medicamento.id)

        val executor = Executors.newFixedThreadPool(2)

        try {
            listaDesejos.saveAndFlush(
                ItemListaDesejo(
                    usuario = usuario,
                    ubs = ubs,
                    medicamento = medicamento,
                    notificar = true,
                ),
            )

            // Obriga as duas threads a obterem a resposta
            // da fonte antes de disputar a trava no banco.
            val ambasConsultaramFonte = CountDownLatch(2)

            given(fonte.buscarPosicoes(ibge, null)).willAnswer {
                ambasConsultaramFonte.countDown()

                check(
                    ambasConsultaramFonte.await(
                        15,
                        TimeUnit.SECONDS,
                    ),
                ) {
                    "As duas sincronizações não chegaram à fonte"
                }

                listOf(posicao(ubs.cnes))
            }

            // Cada chamada utilizará uma transação própria.
            val primeira = executor.submit(
                Callable {
                    sincronizacao.sincronizar(ibge)
                },
            )

            val segunda = executor.submit(
                Callable {
                    sincronizacao.sincronizar(ibge)
                },
            )

            // Ambas devem concluir sem exceções.
            val resultadoPrimeiro = primeira.get(
                30,
                TimeUnit.SECONDS,
            )

            val resultadoSegundo = segunda.get(
                30,
                TimeUnit.SECONDS,
            )

            assertEquals(ibge, resultadoPrimeiro.codigoIbge)
            assertEquals(ibge, resultadoSegundo.codigoIbge)

            // Somente uma transição inicial para
            // DISPONIVEL deve produzir aviso.
            val avisosCriados = avisos
                .findAllByUsuarioIdOrderByCriadoEmDescIdDesc(
                    usuarioId,
                )

            assertEquals(
                1,
                avisosCriados.size,
                "Sincronizações simultâneas não devem duplicar avisos",
            )

            assertEquals(
                TipoAviso.MEDICAMENTO_DISPONIVEL,
                avisosCriados.single().tipo,
            )

            // Também não pode haver itens de estoque duplicados.
            val quantidadeItens = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM item_estoque
                WHERE ubs_id = ?
                  AND medicamento_id = ?
                """.trimIndent(),
                Long::class.java,
                ubsId,
                medicamentoId,
            )

            assertEquals(1L, quantidadeItens)

            val status = jdbc.queryForObject(
                """
                SELECT status
                FROM item_estoque
                WHERE ubs_id = ?
                  AND medicamento_id = ?
                """.trimIndent(),
                String::class.java,
                ubsId,
                medicamentoId,
            )

            assertEquals("DISPONIVEL", status)

            assertTrue(
                avisosCriados.single().chaveEvento.startsWith(
                    "disponibilidade:$ubsId:$medicamentoId:",
                ),
            )
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(30, TimeUnit.SECONDS)

            // A classe não é transacional; removemos apenas
            // os registros pertencentes a este teste.
            jdbc.update(
                "DELETE FROM aviso WHERE usuario_id = ?",
                usuarioId,
            )

            jdbc.update(
                "DELETE FROM item_lista_desejo WHERE usuario_id = ?",
                usuarioId,
            )

            jdbc.update(
                """
                DELETE FROM lote_estoque
                WHERE item_estoque_id IN (
                    SELECT id
                    FROM item_estoque
                    WHERE ubs_id = ?
                )
                """.trimIndent(),
                ubsId,
            )

            jdbc.update(
                "DELETE FROM item_estoque WHERE ubs_id = ?",
                ubsId,
            )

            jdbc.update(
                "DELETE FROM usuario WHERE id = ?",
                usuarioId,
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
