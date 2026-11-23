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
import java.time.ZoneId
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
    private val zonaMaceio = ZoneId.of("America/Maceio")

    // A sincronização grava posições a meia-noite do fuso de Maceió.
    private fun posicao(diasAntesDeHoje: Long): Instant =
        agora.atZone(zonaMaceio).toLocalDate()
            .minusDays(diasAntesDeHoje)
            .atStartOfDay(zonaMaceio)
            .toInstant()

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
        val referencia = posicao(2)
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
            posicao(2),
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
            posicao(0),
        )

        val outroMunicipio = criarUbs(
            "9000105",
            posicao(2),
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
            posicao(2),
        )

        val medicamento = criarMedicamento("BR0267693", "Metformina")
        val cidadao = criarDispositivo("atraso-novo-periodo@teste.com")

        acompanhar(cidadao, ubs, medicamento)

        // Primeiro período de atraso.
        assertEquals(1, servico.verificarMunicipio(ibge))

        val primeiraChave = avisosDo(cidadao).single().chaveEvento

        // A UBS recebe uma posição de ontem, ainda válida neste dia.
        val unidadeAtualizada = unidades.findById(ubs.id!!).orElseThrow()
        unidadeAtualizada.ultimaSincronizacao = posicao(1)
        unidades.saveAndFlush(unidadeAtualizada)

        assertEquals(0, servico.verificarMunicipio(ibge))
        assertEquals(1, avisosDo(cidadao).size)

        // No dia seguinte, a posição já é anterior a ontem.
        instanteAtual = agora.plus(Duration.ofDays(1))

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
    fun `considera atrasada somente posicao anterior a ontem no fuso de Maceio`() {
        val ontem = criarUbs("9000107", posicao(1))
        val anteontem = criarUbs("9000108", posicao(2))
        val medicamento = criarMedicamento("BR0267694", "Metformina")
        val dispositivo = criarDispositivo("atraso-limite@teste.com")

        acompanhar(dispositivo, ontem, medicamento)
        acompanhar(dispositivo, anteontem, medicamento)

        assertEquals(1, servico.verificarMunicipio(ibge))
        val encontrados = avisosDo(dispositivo)
        assertEquals(1, encontrados.size)
        assertTrue(
            encontrados.single().chaveEvento.startsWith("atraso:${anteontem.id}:"),
        )
    }

    @Test
    fun `nao gera aviso logo apos meia noite seguinte para posicao de ontem`() {
        val ontem = criarUbs("9000109", posicao(0))
        val medicamento = criarMedicamento("BR0267695", "Metformina")
        val dispositivo = criarDispositivo("atraso-virada-dia@teste.com")
        acompanhar(dispositivo, ontem, medicamento)

        // 20/11 00:01 em Maceió; a posição de 19/11 ainda é válida.
        instanteAtual = Instant.parse("2026-11-20T03:01:00Z")
        assertEquals(0, servico.verificarMunicipio(ibge))
        assertTrue(avisosDo(dispositivo).isEmpty())

        // 21/11 00:01 em Maceió; a mesma posição é anterior a ontem.
        instanteAtual = Instant.parse("2026-11-21T03:01:00Z")
        assertEquals(1, servico.verificarMunicipio(ibge))
        assertEquals(1, avisosDo(dispositivo).size)
    }
}
