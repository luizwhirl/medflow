package br.ufal.medflow.ubs

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.estoque.ItemEstoque
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.hasItems
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
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
) {
    private lateinit var umGrauSul: Ubs
    private lateinit var doisGrausSul: Ubs

    private fun ubs(nome: String, cnes: String, latitude: Double, longitude: Double = -35.0) = ubsRepository.save(
        Ubs(
            cnes = cnes, nome = nome, tipo = TipoUnidade.UBS, bairro = "Centro", endereco = "Rua A, 1",
            codigoIbgeMunicipio = "2704302", latitude = latitude, longitude = longitude,
        ),
    )

    @BeforeEach
    fun unidades() {
        ubs("Zero", "9000001", -9.0)
        umGrauSul = ubs("Um grau sul", "9000002", -10.0)
        doisGrausSul = ubs("Dois graus sul", "9000003", -11.0)
        ubs("Tres graus norte", "9000004", -6.0)
    }

    private fun proximas(vararg parametros: Pair<String, String>) =
        mockMvc.get("/api/ubs") { parametros.forEach { (k, v) -> param(k, v) } }

    @Test
    fun `ordena da mais proxima para a mais distante e informa a distancia em km`() {
        proximas("lat" to "-9", "lng" to "-35").andExpect {
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
        ubs("Um grau norte", "9000005", -8.0)

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
        ubs("Equador leste", "9000007", 0.0, longitude = 1.0)
        // fora do equador, o gabarito usa outra fórmula (lei esférica dos cossenos), não a do código
        ubs("Sessenta leste", "9000008", 60.0, longitude = 10.0)

        val phi = Math.toRadians(60.0)
        val esperado = Math.round(
            6371.0 * Math.acos(Math.sin(phi) * Math.sin(phi) + Math.cos(phi) * Math.cos(phi) * Math.cos(Math.toRadians(10.0))) * 100,
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
    fun `nenhuma unidade no raio devolve lista vazia`() {
        proximas("lat" to "40", "lng" to "10", "raioKm" to "5").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `distancias iguais desempatam pelo nome`() {
        ubs("Aaa", "9000006", -9.0)

        proximas("lat" to "-9", "lng" to "-35", "raioKm" to "1").andExpect {
            jsonPath("$[0].nome") { value("Aaa") }
            jsonPath("$[1].nome") { value("Zero") }
        }
    }

    @Test
    fun `traz status e quantidade do medicamento so nas unidades que tem o item`() {
        val metformina = medicamentoRepository.save(Medicamento("BR0267690", "Metformina", "850 mg", "comprimido", "Diabetes"))
        itemRepository.save(ItemEstoque(umGrauSul, metformina, StatusEstoque.RESERVADO, 10, Instant.parse("2026-11-03T03:00:00Z")))
        itemRepository.save(ItemEstoque(doisGrausSul, metformina, StatusEstoque.EM_FALTA, 0, Instant.parse("2026-11-03T03:00:00Z")))

        proximas("lat" to "-9", "lng" to "-35", "medicamentoId" to metformina.id.toString()).andExpect {
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

        proximas("lat" to "-9", "lng" to "-35", "medicamentoId" to losartana.id.toString()).andExpect {
            jsonPath("$.length()") { value(4) } // sem linhas duplicadas por causa de outros itens da unidade
            jsonPath("$[1].status") { value("EM_FALTA") }
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
        // (9, 145) é o ponto oposto da unidade "Zero" (-9, -35): a distância é meia circunferência, 6371 * pi = 20015,09 km
        proximas("lat" to "9", "lng" to "145").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(4) }
            jsonPath("$[3].nome") { value("Zero") } // a mais distante vem por último
            jsonPath("$[3].distanciaKm") { value(closeTo(20015.09, 0.01)) }
        }
    }
}
