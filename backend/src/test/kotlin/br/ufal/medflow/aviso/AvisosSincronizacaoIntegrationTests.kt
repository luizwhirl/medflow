package br.ufal.medflow.aviso

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.integracao.FonteEstoque
import br.ufal.medflow.integracao.PosicaoEstoque
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.sincronizacao.SincronizacaoEstoqueService
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Verifica a geração de avisos durante a sincronização real com PostgreSQL. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AvisosSincronizacaoIntegrationTests(
    @Autowired private val sincronizacao: SincronizacaoEstoqueService,
    @Autowired private val unidades: UbsRepository,
    @Autowired private val medicamentos: MedicamentoRepository,
    @Autowired private val avisos: AvisoRepository,
    @Autowired private val em: EntityManager,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @MockitoBean
    private lateinit var fonte: FonteEstoque

    private val ibge = "2702405"
    private val dia = LocalDate.of(2026, 11, 3)

    private lateinit var ubs: Ubs
    private lateinit var medicamento: Medicamento

    @BeforeEach
    fun preparar() {
        ubs = unidades.saveAndFlush(
            Ubs(
                cnes = "9674217",
                nome = "UBS Maria Aparecida Lisboa",
                tipo = TipoUnidade.UBS,
                bairro = "Centro",
                endereco = "Rua Adauto Silva, S/N",
                codigoIbgeMunicipio = ibge,
                latitude = -9.387756,
                longitude = -38.000369,
            ),
        )

        medicamento = medicamentos.saveAndFlush(
            Medicamento(
                catmat = "BR0267691",
                nome = "Metformina",
                concentracao = "850 mg",
                formaFarmaceutica = "comprimido",
                categoria = "Diabetes",
                rename = false,
            ),
        )
    }

    /** Cria uma posição de estoque semelhante às recebidas da BNAFAR. */
    private fun pos(
        quantidade: String,
        dataPosicao: LocalDate = dia,
    ): PosicaoEstoque =
        PosicaoEstoque(
            codigoIbgeMunicipio = ibge,
            cnes = "9674217",
            nomeUnidade = "UBS MARIA APARECIDA LISBOA",
            logradouro = "RUA ADAUTO SILVA",
            numero = "S/N",
            bairro = "CENTRO",
            cep = "57480000",
            telefone = null,
            latitude = -9.387756,
            longitude = -38.000369,
            catmat = "BR0267691U0042",
            descricaoProduto = "METFORMINA, CLORIDRATO 850 MG COMPRIMIDO",
            quantidade = BigDecimal(quantidade),
            numeroLote = "L1",
            validade = LocalDate.of(2027, 10, 31),
            dataPosicao = dataPosicao,
        )

    /** Define os saldos devolvidos em cada execução da sincronização. */
    private fun fonteDevolve(vararg quantidades: String) {
        var stub = given(fonte.buscarPosicoes(ibge, null))

        quantidades.forEach { quantidade ->
            stub = stub.willReturn(listOf(pos(quantidade)))
        }
    }

    /** O UUID representa um navegador que acompanha o medicamento, sem criar conta. */
    private fun criarDispositivo(
        chave: String,
        notificar: Boolean = true,
    ): UUID {
        val dispositivo = UUID.nameUUIDFromBytes(chave.toByteArray(Charsets.UTF_8))
        jdbc.update(
            """
            INSERT INTO item_lista_desejo (dispositivo_id, ubs_id, medicamento_id, notificar)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            dispositivo, ubs.id!!, medicamento.id!!, notificar,
        )
        return dispositivo
    }

    private fun avisosDo(dispositivo: UUID): List<Aviso> {
        em.flush()
        em.clear()
        return avisos.findAllByDispositivoIdOrderByCriadoEmDescIdDesc(dispositivo)
    }

    @Test
    fun `gera avisos na primeira disponibilidade do medicamento`() {
        val primeiro = criarDispositivo("primeiro-sync@teste.com")
        val segundo = criarDispositivo("segundo-sync@teste.com")

        fonteDevolve("100")

        sincronizacao.sincronizar(ibge)

        val avisosPrimeiro = avisosDo(primeiro)
        val avisosSegundo = avisosDo(segundo)

        assertEquals(1, avisosPrimeiro.size)
        assertEquals(1, avisosSegundo.size)

        val aviso = avisosPrimeiro.single()

        assertEquals(
            TipoAviso.MEDICAMENTO_DISPONIVEL,
            aviso.tipo,
        )
        assertEquals("Medicamento disponível", aviso.titulo)
        assertTrue(aviso.texto.contains("Metformina"))
        assertTrue(aviso.texto.contains("UBS Maria Aparecida Lisboa"))
        assertFalse(aviso.lido)

        // Os dois cidadãos foram avisados sobre a mesma transição.
        assertEquals(
            aviso.chaveEvento,
            avisosSegundo.single().chaveEvento,
        )
    }

    @Test
    fun `sincronizacoes repetidas nao duplicam avisos`() {
        val cidadao = criarDispositivo("repeticao-sync@teste.com")

        // A quantidade muda, mas o medicamento permanece disponível.
        fonteDevolve("100", "150", "150")

        sincronizacao.sincronizar(ibge)
        assertEquals(1, avisosDo(cidadao).size)

        sincronizacao.sincronizar(ibge)
        assertEquals(1, avisosDo(cidadao).size)

        sincronizacao.sincronizar(ibge)
        assertEquals(1, avisosDo(cidadao).size)
    }

    @Test
    fun `nao duplica aviso nao lido quando medicamento volta a ficar disponivel`() {
        val cidadao = criarDispositivo("reposicao-sync@teste.com")

        // Sem estoque, reposição, falta novamente e nova reposição.
        fonteDevolve("0", "100", "0", "100")

        sincronizacao.sincronizar(ibge)
        assertTrue(avisosDo(cidadao).isEmpty())

        sincronizacao.sincronizar(ibge)
        val primeiraDisponibilidade = avisosDo(cidadao)

        assertEquals(1, primeiraDisponibilidade.size)
        assertFalse(primeiraDisponibilidade.single().lido)

        val chaveOriginal = primeiraDisponibilidade.single().chaveEvento

        sincronizacao.sincronizar(ibge)
        assertEquals(1, avisosDo(cidadao).size)

        sincronizacao.sincronizar(ibge)
        val aposNovaReposicao = avisosDo(cidadao)

        // O aviso anterior continua não lido.
        // Não deve existir outro aviso pendente do mesmo item.
        assertEquals(1, aposNovaReposicao.size)
        assertEquals(
            chaveOriginal,
            aposNovaReposicao.single().chaveEvento,
        )
        assertFalse(aposNovaReposicao.single().lido)
    }

    @Test
    fun `gera novo aviso apos cidadao ler a disponibilidade anterior`() {
        val cidadao = criarDispositivo("leitura-sync@teste.com")

        // Primeira reposição, nova falta e segunda reposição.
        fonteDevolve("0", "100", "0", "100")

        sincronizacao.sincronizar(ibge)
        assertTrue(avisosDo(cidadao).isEmpty())

        sincronizacao.sincronizar(ibge)
        val primeiroAviso = avisosDo(cidadao).single()

        assertFalse(primeiroAviso.lido)

        // O cidadão marca o primeiro aviso como lido.
        val atualizados = avisos.marcarTodosComoLidos(
            cidadao,
        )

        assertEquals(1, atualizados)
        assertTrue(avisosDo(cidadao).single().lido)

        // O estoque deixa de estar disponível.
        sincronizacao.sincronizar(ibge)
        assertEquals(1, avisosDo(cidadao).size)

        // Uma nova transição pode gerar outro aviso.
        sincronizacao.sincronizar(ibge)

        val historico = avisosDo(cidadao)

        assertEquals(2, historico.size)

        assertNotEquals(
            historico[0].chaveEvento,
            historico[1].chaveEvento,
        )

        assertEquals(
            1,
            historico.count { !it.lido },
        )

        assertEquals(
            1,
            historico.count { it.lido },
        )

        assertTrue(
            historico.all {
                it.tipo == TipoAviso.MEDICAMENTO_DISPONIVEL
            },
        )
    }

    @Test
    fun `leitura de um cidadao nao libera duplicatas para outro`() {
        val primeiro = criarDispositivo("leitura-primeiro@teste.com")
        val segundo = criarDispositivo("leitura-segundo@teste.com")

        fonteDevolve("100", "0", "100")

        // Ambos recebem o aviso inicial.
        sincronizacao.sincronizar(ibge)

        assertEquals(1, avisosDo(primeiro).size)
        assertEquals(1, avisosDo(segundo).size)

        // Somente o primeiro cidadão marca o aviso como lido.
        assertEquals(
            1,
            avisos.marcarTodosComoLidos(primeiro),
        )

        // O medicamento fica indisponível e depois disponível.
        sincronizacao.sincronizar(ibge)
        sincronizacao.sincronizar(ibge)

        val avisosPrimeiro = avisosDo(primeiro)
        val avisosSegundo = avisosDo(segundo)

        // O primeiro recebe outro aviso.
        assertEquals(2, avisosPrimeiro.size)
        assertEquals(1, avisosPrimeiro.count { !it.lido })

        // O segundo continua com o aviso original não lido.
        assertEquals(1, avisosSegundo.size)
        assertFalse(avisosSegundo.single().lido)
    }

    @Test
    fun `nao avisa cidadao que desativou notificacoes`() {
        val interessado = criarDispositivo(
            "interessado-sync@teste.com",
            notificar = true,
        )

        val desativado = criarDispositivo(
            "desativado-sync@teste.com",
            notificar = false,
        )

        fonteDevolve("100")

        sincronizacao.sincronizar(ibge)

        assertEquals(1, avisosDo(interessado).size)
        assertTrue(avisosDo(desativado).isEmpty())
    }

    @Test
    fun `gera aviso quando estoque reservado passa a disponivel`() {
        val cidadao = criarDispositivo("reservado-sync@teste.com")

        // Pela regra de estoque existente, 5 unidades ficam RESERVADAS.
        fonteDevolve("5", "100")

        sincronizacao.sincronizar(ibge)

        // Ainda não existe disponibilidade efetiva.
        assertTrue(avisosDo(cidadao).isEmpty())

        sincronizacao.sincronizar(ibge)

        assertEquals(1, avisosDo(cidadao).size)
    }
}
