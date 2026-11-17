package br.ufal.medflow.ubs

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.estoque.ItemEstoque
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasItems
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Banco vazio (sem o perfil "dev"). As unidades ficam todas na mesma longitude, então a distância até cada uma
 * é exata: 1 grau de latitude = 6371 * pi / 180 = 111,19 km (gabarito que não depende da fórmula do código).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class UbsProximidadeIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val ubsRepository: UbsRepository,
    @Autowired private val medicamentoRepository: MedicamentoRepository,
    @Autowired private val itemRepository: ItemEstoqueRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private lateinit var umGrauSul: Ubs
    private lateinit var doisGrausSul: Ubs
    private var proximoCnes = 8_000_000

    private fun ubs(nome: String, latitude: Double, longitude: Double = -35.0) = ubsRepository.save(
        Ubs(
            cnes = (proximoCnes++).toString(), nome = nome, tipo = TipoUnidade.UBS, bairro = "Centro",
            endereco = "Rua A, 1", codigoIbgeMunicipio = "2704302", latitude = latitude, longitude = longitude,
        ),
    )

    @BeforeEach
    fun unidades() {
        ubs("Zero", -9.0)
        umGrauSul = ubs("Um grau sul", -10.0)
        doisGrausSul = ubs("Dois graus sul", -11.0)
        ubs("Tres graus norte", -6.0)
    }

    private fun proximas(vararg parametros: Pair<String, String>) =
        mockMvc.get("/api/ubs") { parametros.forEach { (k, v) -> param(k, v) } }

    @Test
    fun `ordena da mais proxima para a mais distante e informa a distancia em km`() {
        proximas("lat" to "-9", "lng" to "-35", "raioKm" to "400").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(4) }
            jsonPath("$[0].nome") { value("Zero") }
            jsonPath("$[0].distanciaKm") { value(0.0) }
            jsonPath("$[1].nome") { value("Um grau sul") }
            jsonPath("$[1].distanciaKm") { value(111.19) }
            jsonPath("$[2].nome") { value("Dois graus sul") }
            jsonPath("$[2].distanciaKm") { value(222.39) }
            jsonPath("$[3].nome") { value("Tres graus norte") }
            jsonPath("$[3].distanciaKm") { value(333.58) }
        }
    }

    @Test
    fun `a distancia vale para qualquer lado do ponto`() {
        // 1 grau ao norte e 1 grau ao sul de (-9, -35) ficam à mesma distância
        ubs("Um grau norte", -8.0)

        proximas("lat" to "-9", "lng" to "-35", "raioKm" to "112").andExpect {
            jsonPath("$.length()") { value(3) }
            jsonPath("$[1].nome") { value("Um grau norte") }
            jsonPath("$[1].distanciaKm") { value(111.19) }
            jsonPath("$[2].nome") { value("Um grau sul") }
            jsonPath("$[2].distanciaKm") { value(111.19) }
        }
    }

    @Test
    fun `a diferenca de longitude tambem conta na distancia`() {
        // no equador, 1 grau de longitude também é 111,19 km
        ubs("Equador leste", 0.0, longitude = 1.0)
        // fora do equador, o gabarito usa outra fórmula (lei esférica dos cossenos), não a do código
        ubs("Sessenta leste", 60.0, longitude = 10.0)

        val phi = Math.toRadians(60.0)
        val esperado = Math.round(
            6371.0 * Math.acos(sin(phi) * sin(phi) + cos(phi) * cos(phi) * cos(Math.toRadians(10.0))) * 100,
        ) / 100.0

        proximas("lat" to "0", "lng" to "0", "raioKm" to "112").andExpect {
            jsonPath("$[?(@.nome == 'Equador leste')].distanciaKm") { value(111.19) }
        }
        proximas("lat" to "60", "lng" to "0", "raioKm" to "600").andExpect {
            jsonPath("$[?(@.nome == 'Sessenta leste')].distanciaKm") { value(esperado) }
        }
        // 10 graus de longitude a 60 graus de latitude são bem menos que 10 graus no equador (1111,95 km)
        assertTrue(esperado in 540.0..560.0, "gabarito fora do esperado: $esperado")
    }

    @Test
    fun `raio limita o resultado`() {
        proximas("lat" to "-9", "lng" to "-35", "raioKm" to "150").andExpect {
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].nome") { value("Zero") }
            jsonPath("$[1].nome") { value("Um grau sul") }
        }
        proximas("lat" to "-9", "lng" to "-35", "raioKm" to "250").andExpect { jsonPath("$.length()") { value(3) } }
        proximas("lat" to "-9", "lng" to "-35", "raioKm" to "0.5").andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].nome") { value("Zero") }
        }
    }

    @Test
    fun `sem raioKm usa 20 km e nao devolve o banco inteiro`() {
        ubs("Dez km", -9.0 - 10.0 / 111.1949)
        ubs("Trinta km", -9.0 - 30.0 / 111.1949)

        proximas("lat" to "-9", "lng" to "-35").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].nome") { value("Zero") }
            jsonPath("$[1].nome") { value("Dez km") }
            jsonPath("$[1].distanciaKm") { value(closeTo(10.0, 0.01)) }
        }
    }

    @Test
    fun `nenhuma unidade no raio devolve lista vazia`() {
        proximas("lat" to "40", "lng" to "10", "raioKm" to "5").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `distancias iguais desempatam pelo nome`() {
        ubs("Aaa", -9.0)

        proximas("lat" to "-9", "lng" to "-35", "raioKm" to "1").andExpect {
            jsonPath("$[0].nome") { value("Aaa") }
            jsonPath("$[1].nome") { value("Zero") }
        }
    }

    /**
     * A caixa de latitude/longitude só pode descartar o que está fora do raio. Em volta de cada ponto há um anel de
     * unidades logo dentro (99%) e outro logo fora (101%) do raio, em vários rumos, calculados pela fórmula do
     * ponto de destino (trigonometria esférica, outra fórmula que a da distância).
     */
    @Test
    fun `a caixa nunca corta unidade que esta dentro do raio, em qualquer latitude`() {
        listOf(
            Triple(-9.6, -35.7, 15.0), // perto de Maceió, longe das unidades-base
            Triple(70.0, 20.0, 15.0), // latitude alta: a faixa de longitude fica bem mais larga
            Triple(89.9, 10.0, 50.0), // o raio alcança o polo
            Triple(0.0, 179.9, 50.0), // cruza o meridiano 180
        ).forEach { (lat, lng, raioKm) ->
            val dentro = anel(lat, lng, raioKm * 0.99, "dentro $lat,$lng")
            anel(lat, lng, raioKm * 1.01, "fora $lat,$lng")

            proximas("lat" to "$lat", "lng" to "$lng", "raioKm" to "$raioKm").andExpect {
                status { isOk() }
                jsonPath("$[*].nome") { value(containsInAnyOrder(*dentro.toTypedArray())) }
            }
        }
    }

    /** Cria unidades a [distanciaKm] do ponto, de 15 em 15 graus de rumo, e devolve os nomes. */
    private fun anel(lat: Double, lng: Double, distanciaKm: Double, prefixo: String): List<String> =
        (0 until 360 step 15).map { rumo ->
            val delta = distanciaKm / 6371.0
            val phi1 = Math.toRadians(lat)
            val theta = Math.toRadians(rumo.toDouble())
            val phi2 = asin(sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta))
            val lambda2 = Math.toRadians(lng) +
                atan2(sin(theta) * sin(delta) * cos(phi1), cos(delta) - sin(phi1) * sin(phi2))
            val lngNormalizada = ((Math.toDegrees(lambda2) + 540) % 360) - 180
            "$prefixo rumo $rumo".also { ubs(it, Math.toDegrees(phi2), lngNormalizada) }
        }

    @Test
    fun `traz status e quantidade do medicamento so nas unidades que tem o item`() {
        val metformina = medicamentoRepository.save(Medicamento("BR0267690", "Metformina", "850 mg", "comprimido", "Diabetes"))
        itemRepository.save(ItemEstoque(umGrauSul, metformina, StatusEstoque.RESERVADO, 10, Instant.parse("2026-11-03T03:00:00Z")))
        itemRepository.save(ItemEstoque(doisGrausSul, metformina, StatusEstoque.EM_FALTA, 0, Instant.parse("2026-11-03T03:00:00Z")))

        proximas("lat" to "-9", "lng" to "-35", "raioKm" to "400", "medicamentoId" to metformina.id.toString()).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(4) } // a unidade sem o item continua na lista
            jsonPath("$[0].nome") { value("Zero") }
            jsonPath("$[0].status") { doesNotExist() }
            jsonPath("$[0].quantidade") { doesNotExist() }
            jsonPath("$[1].status") { value("RESERVADO") }
            jsonPath("$[1].quantidade") { value(10) }
            jsonPath("$[2].status") { value("EM_FALTA") }
            jsonPath("$[2].quantidade") { value(0) }
            jsonPath("$[3].status") { doesNotExist() }
        }
    }

    @Test
    fun `o status e o do medicamento pedido, nao de outro`() {
        val metformina = medicamentoRepository.save(Medicamento("BR0267690", "Metformina", "850 mg", "comprimido", "Diabetes"))
        val losartana = medicamentoRepository.save(Medicamento("BR0268856", "Losartana", "50 mg", "comprimido", "Hipertensão"))
        itemRepository.save(ItemEstoque(umGrauSul, metformina, StatusEstoque.DISPONIVEL, 100, Instant.parse("2026-11-03T03:00:00Z")))
        itemRepository.save(ItemEstoque(umGrauSul, losartana, StatusEstoque.EM_FALTA, 0, Instant.parse("2026-11-03T03:00:00Z")))

        proximas("lat" to "-9", "lng" to "-35", "raioKm" to "400", "medicamentoId" to losartana.id.toString()).andExpect {
            jsonPath("$.length()") { value(4) } // sem linhas duplicadas por causa de outros itens da unidade
            jsonPath("$[1].status") { value("EM_FALTA") }
        }
    }

    @Test
    fun `sem medicamentoId nenhuma unidade traz status mesmo tendo estoque`() {
        val metformina = medicamentoRepository.save(Medicamento("BR0267690", "Metformina", "850 mg", "comprimido", "Diabetes"))
        itemRepository.save(ItemEstoque(umGrauSul, metformina, StatusEstoque.DISPONIVEL, 100, Instant.parse("2026-11-03T03:00:00Z")))

        proximas("lat" to "-9", "lng" to "-35", "raioKm" to "400").andExpect {
            jsonPath("$.length()") { value(4) }
            jsonPath("$[1].status") { doesNotExist() }
        }
    }

    @Test
    fun `os parametros aparecem documentados no Swagger`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/ubs'].get.parameters[*].name") { value(hasItems("lat", "lng", "raioKm", "medicamentoId")) }
            jsonPath("$.paths['/api/ubs'].get.parameters[?(@.name == 'raioKm')].description") { isNotEmpty() }
        }
    }

    @Test
    fun `sem coordenadas lista por nome e sem os campos novos`() {
        mockMvc.get("/api/ubs").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(4) }
            jsonPath("$[0].nome") { value("Dois graus sul") }
            jsonPath("$[3].nome") { value("Zero") }
            jsonPath("$[0].distanciaKm") { doesNotExist() }
            jsonPath("$[0].status") { doesNotExist() }
        }
    }

    @Test
    fun `medicamento inexistente retorna 404`() {
        proximas("lat" to "-9", "lng" to "-35", "medicamentoId" to "999999").andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
    }

    @Test
    fun `ponto antipoda nao quebra a conta`() {
        // (9, 145) é o ponto oposto da unidade "Zero" (-9, -35): a distância é meia circunferência, 6371 * pi = 20015,09 km.
        // Chamada direta ao repositório, com a caixa inteira, porque a API limita pelo raio.
        val lat = Math.toRadians(9.0)
        val linhas = ubsRepository.buscarProximas(
            latRad = lat, lngRad = Math.toRadians(145.0), cosLat = cos(lat), raioKm = 20_100.0,
            latMin = -90.0, latMax = 90.0, lngMin = -180.0, lngMax = 180.0, medicamentoId = null,
        )

        assertEquals(4, linhas.size)
        assertEquals("Zero", (linhas.last()[0] as Ubs).nome) // a mais distante vem por último
        assertEquals(20015.09, linhas.last()[1] as Double, 0.01)
    }

    @Test
    fun `o indice de localizacao existe`() {
        val total = jdbc.queryForObject("select count(*) from pg_indexes where indexname = 'idx_ubs_localizacao'", Long::class.java)

        assertEquals(1L, total)
    }
}
