package br.ufal.medflow.sincronizacao

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.auth.JwtService
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import br.ufal.medflow.integracao.FonteEstoque
import br.ufal.medflow.integracao.Geocodificador
import br.ufal.medflow.integracao.LimitesMunicipais
import br.ufal.medflow.integracao.LocalEncontrado
import br.ufal.medflow.integracao.Ponto
import br.ufal.medflow.integracao.PosicaoEstoque
import br.ufal.medflow.integracao.quadrado
import br.ufal.medflow.ubs.OrigemLocalizacao
import br.ufal.medflow.ubs.UbsProximidadeService
import br.ufal.medflow.ubs.UbsRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Origem da localização das UBS (PDS-21): coordenada da fonte conferida com o contorno do município, busca no mapa
 * quando ela cai fora, e correção do pino pelo gestor. Banco vazio (perfil "test"); cada teste desfaz o que gravou.
 * O município é um quadrado de ~22 km em volta de Delmiro Gouveia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class LocalizacaoUbsIntegrationTests(
    @Autowired private val servico: SincronizacaoEstoqueService,
    @Autowired private val ubsRepository: UbsRepository,
    @Autowired private val proximidade: UbsProximidadeService,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val jwtService: JwtService,
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val em: EntityManager,
) {
    @MockitoBean
    private lateinit var fonte: FonteEstoque

    @MockitoBean
    private lateinit var limites: LimitesMunicipais

    @MockitoBean
    private lateinit var geocodificador: Geocodificador

    private val ibge = "2702405"
    private val centro = Ponto(-9.387756, -38.000369)
    private val noMunicipio = Ponto(-9.390, -38.010)
    private val noMar = Ponto(-9.39, -34.9)
    private val delmiro = quadrado(centro, meioLadoGraus = 0.1)

    @BeforeEach
    fun preparar() {
        given(limites.limite(anyString())).willReturn(delmiro)
    }

    private fun pos(onde: Ponto, cnes: String = "9674217") = PosicaoEstoque(
        codigoIbgeMunicipio = ibge, cnes = cnes, nomeUnidade = "UBS MARIA APARECIDA LISBOA", logradouro = "RUA ADAUTO SILVA",
        numero = "S/N", bairro = "CENTRO", cep = "57480000", telefone = null, latitude = onde.latitude, longitude = onde.longitude,
        catmat = "BR0267691U0042", descricaoProduto = "METFORMINA, CLORIDRATO 850 MG COMPRIMIDO", quantidade = BigDecimal("100"),
        numeroLote = "L1", validade = LocalDate.of(2027, 10, 31), dataPosicao = LocalDate.of(2026, 11, 3),
    )

    private fun fonteDevolve(vararg rodadas: List<PosicaoEstoque>) {
        var stub = given(fonte.buscarPosicoes(ibge, null))
        rodadas.forEach { stub = stub.willReturn(it) }
    }

    private fun geocodificadorAcha(local: LocalEncontrado?) {
        given(geocodificador.localizar(anyString(), qualquer(), qualquer(), anyDouble())).willReturn(local)
    }

    /** Matchers do Mockito que o Kotlin aceita em parâmetros não nulos (o any() e o eq() devolvem null). */
    @Suppress("UNCHECKED_CAST")
    private fun <T> qualquer(): T {
        ArgumentMatchers.any<T>()
        return null as T
    }

    private fun <T> igual(valor: T): T {
        ArgumentMatchers.eq(valor)
        return valor
    }

    private fun ubs() = requireNotNull(ubsRepository.findByCnesIn(listOf("9674217")).singleOrNull())

    private fun visivelNoRaio(): Boolean =
        proximidade.buscar(centro.latitude, centro.longitude, raioKm = 20.0, medicamentoId = null).any { it.cnes == "9674217" }

    @Test
    fun `coordenada da fonte dentro do municipio vale e a UBS aparece no raio`() {
        fonteDevolve(listOf(pos(noMunicipio)))

        val resultado = servico.sincronizar(ibge)

        assertEquals(OrigemLocalizacao.FONTE, ubs().localizacaoOrigem)
        assertEquals(mapOf(OrigemLocalizacao.FONTE to 1), resultado.localizacoes)
        assertEquals(true, visivelNoRaio())
        verify(geocodificador, never()).localizar(anyString(), qualquer(), qualquer(), anyDouble())
    }

    @Test
    fun `coordenada no mar e trocada pelo predio achado no mapa`() {
        val predio = Ponto(-9.3880, -38.0010)
        geocodificadorAcha(LocalEncontrado(predio, OrigemLocalizacao.LUGAR))
        fonteDevolve(listOf(pos(noMar)))

        servico.sincronizar(ibge)

        val u = ubs()
        assertEquals(OrigemLocalizacao.LUGAR, u.localizacaoOrigem)
        assertEquals(predio, Ponto(u.latitude, u.longitude))
        assertEquals(true, visivelNoRaio())
        verify(geocodificador).localizar(igual("UBS MARIA APARECIDA LISBOA"), igual("CENTRO"), igual(delmiro), anyDouble())
    }

    @Test
    fun `sem o predio, fica no centro do bairro e aparece como aproximada`() {
        geocodificadorAcha(LocalEncontrado(noMunicipio, OrigemLocalizacao.BAIRRO))
        fonteDevolve(listOf(pos(noMar)))

        servico.sincronizar(ibge)
        em.flush()

        assertEquals(OrigemLocalizacao.BAIRRO, ubs().localizacaoOrigem)
        mockMvc.get("/api/ubs/${ubs().id}").andExpect {
            status { isOk() }
            jsonPath("$.localizacaoAproximada") { value(true) }
            jsonPath("$.latitude") { value(noMunicipio.latitude) }
        }
    }

    @Test
    fun `sem nada confiavel, a UBS some da lista, do raio e do detalhe ate alguem corrigir`() {
        geocodificadorAcha(null)
        fonteDevolve(listOf(pos(noMar)))

        servico.sincronizar(ibge)
        em.flush()

        val u = ubs()
        assertEquals(OrigemLocalizacao.INVALIDA, u.localizacaoOrigem)
        assertEquals(false, visivelNoRaio())
        assertEquals(emptyList(), ubsRepository.findVisiveis().filter { it.cnes == "9674217" })
        assertNull(ubsRepository.findVisivel(requireNotNull(u.id)))
        mockMvc.get("/api/ubs/${u.id}").andExpect { status { isNotFound() } }
    }

    @Test
    fun `coordenada dentro do municipio mas longe do bairro informado tambem e trocada`() {
        val centroDoBairro = Ponto(-9.47, -38.08) // ~12 km do ponto da fonte, ainda no município
        given(geocodificador.centroDoBairro(anyString(), qualquer(), anyDouble())).willReturn(centroDoBairro)
        geocodificadorAcha(LocalEncontrado(centroDoBairro, OrigemLocalizacao.BAIRRO))
        fonteDevolve(listOf(pos(centro)))

        servico.sincronizar(ibge)

        val u = ubs()
        assertEquals(OrigemLocalizacao.BAIRRO, u.localizacaoOrigem)
        assertEquals(centroDoBairro, Ponto(u.latitude, u.longitude))
    }

    @Test
    fun `coordenada perto do centro do bairro vale`() {
        given(geocodificador.centroDoBairro(anyString(), qualquer(), anyDouble())).willReturn(Ponto(-9.40, -38.01))
        fonteDevolve(listOf(pos(centro)))

        servico.sincronizar(ibge)

        assertEquals(OrigemLocalizacao.FONTE, ubs().localizacaoOrigem)
        verify(geocodificador, never()).localizar(anyString(), qualquer(), qualquer(), anyDouble())
    }

    @Test
    fun `localizacao achada antes nao e procurada de novo enquanto a fonte continua errada`() {
        val predio = Ponto(-9.3880, -38.0010)
        geocodificadorAcha(LocalEncontrado(predio, OrigemLocalizacao.LUGAR))
        fonteDevolve(listOf(pos(noMar)), listOf(pos(noMar)))

        servico.sincronizar(ibge)
        servico.sincronizar(ibge)

        verify(geocodificador).localizar(anyString(), qualquer(), qualquer(), anyDouble()) // uma vez só
        assertEquals(OrigemLocalizacao.LUGAR, ubs().localizacaoOrigem)
    }

    @Test
    fun `quando a fonte passa a mandar uma coordenada boa, ela volta a valer`() {
        geocodificadorAcha(LocalEncontrado(noMunicipio, OrigemLocalizacao.BAIRRO))
        fonteDevolve(listOf(pos(noMar)), listOf(pos(centro)))

        servico.sincronizar(ibge)
        servico.sincronizar(ibge)

        val u = ubs()
        assertEquals(OrigemLocalizacao.FONTE, u.localizacaoOrigem)
        assertEquals(centro, Ponto(u.latitude, u.longitude))
    }

    private fun tokenDeGestor(): String {
        val gestora = usuarios.save(Usuario("Gestora", "gestora-loc@medflow.local", "x", PapelUsuario.GESTOR))
        return jwtService.emitir(gestora).token
    }

    @Test
    fun `gestor corrige o pino, a UBS volta para o mapa e a sincronizacao nao sobrescreve`() {
        geocodificadorAcha(null)
        fonteDevolve(listOf(pos(noMar)), listOf(pos(noMunicipio)))
        servico.sincronizar(ibge)
        em.flush()
        val id = requireNotNull(ubs().id)
        val certo = Ponto(-9.3891, -38.0022)

        mockMvc.put("/api/gestao/ubs/$id/localizacao") {
            header("Authorization", "Bearer ${tokenDeGestor()}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"latitude": ${certo.latitude}, "longitude": ${certo.longitude}}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.origem") { value("MANUAL") }
            jsonPath("$.aproximada") { value(false) }
        }
        em.flush()
        assertEquals(true, visivelNoRaio())

        servico.sincronizar(ibge) // a fonte agora manda outra coordenada, também dentro do município

        val u = ubs()
        assertEquals(OrigemLocalizacao.MANUAL, u.localizacaoOrigem)
        assertEquals(certo, Ponto(u.latitude, u.longitude))
    }

    @Test
    fun `pino fora do municipio e recusado`() {
        fonteDevolve(listOf(pos(noMunicipio)))
        servico.sincronizar(ibge)
        em.flush()

        mockMvc.put("/api/gestao/ubs/${ubs().id}/localizacao") {
            header("Authorization", "Bearer ${tokenDeGestor()}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"latitude": ${noMar.latitude}, "longitude": ${noMar.longitude}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("O ponto fica fora do município da UBS") }
        }
        assertEquals(OrigemLocalizacao.FONTE, ubs().localizacaoOrigem)
    }

    @Test
    fun `consulta a localizacao exige gestor`() {
        fonteDevolve(listOf(pos(noMunicipio)))
        servico.sincronizar(ibge)
        em.flush()
        val id = ubs().id

        mockMvc.get("/api/gestao/ubs/$id/localizacao").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/gestao/ubs/$id/localizacao") {
            header("Authorization", "Bearer ${tokenDeGestor()}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.origem") { value("FONTE") }
            jsonPath("$.bairro") { value("Centro") }
        }
        assertNotNull(id)
    }
}
