package br.ufal.medflow.aviso

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Testes da geração de avisos sobre UBS com dados atrasados. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AvisoAtrasoServiceIntegrationTests(
    @Autowired private val servico: AvisoAtrasoService,
    @Autowired private val unidades: UbsRepository,
    @Autowired private val medicamentos: MedicamentoRepository,
    @Autowired private val avisos: AvisoRepository,
    @Autowired private val em: EntityManager,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @MockitoBean
    private lateinit var relogio: Clock

    private val agora = Instant.parse("2026-11-19T20:00:00Z")
    private var instanteAtual = agora

    private val ibge = "2702405"

    @BeforeEach
    fun prepararRelogio() {
        instanteAtual = agora
        given(relogio.instant()).willAnswer { instanteAtual }
    }

    private fun criarUbs(
        cnes: String,
        ultimaSincronizacao: Instant?,
        codigoIbge: String = ibge,
    ): Ubs =
        unidades.saveAndFlush(
            Ubs(
                cnes = cnes,
                nome = "UBS Teste $cnes",
                tipo = TipoUnidade.UBS,
                bairro = "Centro",
                endereco = "Rua A, 1",
                codigoIbgeMunicipio = codigoIbge,
                latitude = -9.5,
                longitude = -35.7,
                ultimaSincronizacao = ultimaSincronizacao,
            ),
        )

    private fun criarMedicamento(
        catmat: String,
        nome: String,
    ): Medicamento =
        medicamentos.saveAndFlush(
            Medicamento(
                catmat = catmat,
                nome = nome,
                concentracao = "500 mg",
                formaFarmaceutica = "comprimido",
                categoria = "Geral",
                rename = false,
            ),
        )

    private fun criarDispositivo(chave: String): UUID =
        UUID.nameUUIDFromBytes(chave.toByteArray(Charsets.UTF_8))

    private fun acompanhar(
        dispositivo: UUID,
        ubs: Ubs,
        medicamento: Medicamento,
        notificar: Boolean = true,
    ) {
        jdbc.update(
            """
            INSERT INTO item_lista_desejo (dispositivo_id, ubs_id, medicamento_id, notificar)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            dispositivo, ubs.id!!, medicamento.id!!, notificar,
        )
    }

    private fun avisosDo(dispositivo: UUID): List<Aviso> {
        em.flush()
        em.clear()
        return avisos.findAllByDispositivoIdOrderByCriadoEmDescIdDesc(dispositivo)
    }

    @Test
    fun `gera um aviso por cidadao mesmo com varios medicamentos na ubs`() {
        val referencia = agora.minus(Duration.ofHours(48))
        val ubs = criarUbs("9000101", referencia)

        val metformina = criarMedicamento("BR0267690", "Metformina")
        val losartana = criarMedicamento("BR0268856", "Losartana")

        val primeiro = criarDispositivo("atraso-primeiro@teste.com")
        val segundo = criarDispositivo("atraso-segundo@teste.com")
        val desativado = criarDispositivo("atraso-desativado@teste.com")

        acompanhar(primeiro, ubs, metformina)
        acompanhar(primeiro, ubs, losartana)
        acompanhar(segundo, ubs, metformina)
        acompanhar(desativado, ubs, metformina, notificar = false)

        val criados = servico.verificarMunicipio(ibge)

        assertEquals(2, criados)

        val avisosPrimeiro = avisosDo(primeiro)
        val avisosSegundo = avisosDo(segundo)

        assertEquals(1, avisosPrimeiro.size)
        assertEquals(1, avisosSegundo.size)
        assertTrue(avisosDo(desativado).isEmpty())

        val aviso = avisosPrimeiro.single()

        assertEquals(TipoAviso.UBS_DADOS_ATRASADOS, aviso.tipo)
        assertEquals("Dados da UBS desatualizados", aviso.titulo)
        assertTrue(aviso.texto.contains(ubs.nome))
        assertFalse(aviso.lido)
        assertEquals(agora, aviso.criadoEm)

        assertEquals(
            aviso.chaveEvento,
            avisosSegundo.single().chaveEvento,
        )

        assertEquals(
            "atraso:${ubs.id}:${referencia.toEpochMilli()}",
            aviso.chaveEvento,
        )
    }

    @Test
    fun `verificacoes repetidas nao geram avisos duplicados`() {
        val ubs = criarUbs(
            "9000102",
            agora.minus(Duration.ofHours(36)),
        )

        val medicamento = criarMedicamento("BR0267691", "Metformina")
        val cidadao = criarDispositivo("atraso-repetido@teste.com")

        acompanhar(cidadao, ubs, medicamento)

        assertEquals(1, servico.verificarMunicipio(ibge))
        assertEquals(0, servico.verificarMunicipio(ibge))
        assertEquals(0, servico.verificarMunicipio(ibge))

        assertEquals(1, avisosDo(cidadao).size)
    }

    @Test
    fun `considera ubs sem data e ignora unidades atualizadas ou de outro municipio`() {
        val semData = criarUbs("9000103", null)

        val atualizada = criarUbs(
            "9000104",
            agora.minus(Duration.ofHours(12)),
        )

        val outroMunicipio = criarUbs(
            "9000105",
            agora.minus(Duration.ofHours(48)),
            codigoIbge = "2704302",
        )

        val medicamento = criarMedicamento("BR0267692", "Metformina")
        val cidadao = criarDispositivo("atraso-sem-data@teste.com")

        acompanhar(cidadao, semData, medicamento)
        acompanhar(cidadao, atualizada, medicamento)
        acompanhar(cidadao, outroMunicipio, medicamento)

        assertEquals(1, servico.verificarMunicipio(ibge))

        val encontrados = avisosDo(cidadao)

        assertEquals(1, encontrados.size)
        assertEquals(
            "atraso:${semData.id}:sem-data",
            encontrados.single().chaveEvento,
        )
    }

    @Test
    fun `gera novo aviso quando dados atualizados voltam a ficar atrasados`() {
        val ubs = criarUbs(
            "9000106",
            agora.minus(Duration.ofHours(48)),
        )

        val medicamento = criarMedicamento("BR0267693", "Metformina")
        val cidadao = criarDispositivo("atraso-novo-periodo@teste.com")

        acompanhar(cidadao, ubs, medicamento)

        // Primeiro período de atraso.
        assertEquals(1, servico.verificarMunicipio(ibge))

        val primeiraChave = avisosDo(cidadao).single().chaveEvento

        // A UBS recebe uma posição atualizada, com 12 horas de idade.
        val unidadeAtualizada = unidades.findById(ubs.id!!).orElseThrow()
        unidadeAtualizada.ultimaSincronizacao =
            agora.minus(Duration.ofHours(12))
        unidades.saveAndFlush(unidadeAtualizada)

        assertEquals(0, servico.verificarMunicipio(ibge))
        assertEquals(1, avisosDo(cidadao).size)

        // Avança 13 horas. A mesma posição agora tem 25 horas
        // de idade e passa a ser considerada atrasada.
        instanteAtual = agora.plus(Duration.ofHours(13))

        assertEquals(1, servico.verificarMunicipio(ibge))

        val encontrados = avisosDo(cidadao)

        assertEquals(2, encontrados.size)

        val segundaChave = encontrados.first {
            it.chaveEvento != primeiraChave
        }.chaveEvento

        assertNotEquals(primeiraChave, segundaChave)
        assertTrue(
            encontrados.all {
                it.tipo == TipoAviso.UBS_DADOS_ATRASADOS
            },
        )
    }

    @Test
    fun `respeita o limite padrao de vinte e quatro horas`() {
        val noLimite = criarUbs(
            "9000107",
            agora.minus(Duration.ofHours(24)),
        )

        val ultrapassouLimite = criarUbs(
            "9000108",
            agora.minus(Duration.ofHours(24)).minusSeconds(1),
        )

        val medicamento = criarMedicamento("BR0267694", "Metformina")
        val cidadao = criarDispositivo("atraso-limite@teste.com")

        acompanhar(cidadao, noLimite, medicamento)
        acompanhar(cidadao, ultrapassouLimite, medicamento)

        // Exatamente 24 horas não é considerado atraso.
        // A partir de 24 horas e 1 segundo, gera aviso.
        assertEquals(1, servico.verificarMunicipio(ibge))

        val encontrados = avisosDo(cidadao)

        assertEquals(1, encontrados.size)
        assertTrue(
            encontrados.single().chaveEvento.startsWith(
                "atraso:${ultrapassouLimite.id}:",
            ),
        )
    }
}
