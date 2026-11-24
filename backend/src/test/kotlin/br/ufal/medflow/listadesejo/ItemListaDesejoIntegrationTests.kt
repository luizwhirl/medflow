package br.ufal.medflow.listadesejo

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.listadesejo.ItemListaDesejoController.Companion.CABECALHO_DISPOSITIVO
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertNotNull

/** Lista de desejos sem login, com PostgreSQL: o dono é o aparelho do cabeçalho X-Dispositivo-Id. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ItemListaDesejoIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val ubsRepository: UbsRepository,
    @Autowired private val medicamentoRepository: MedicamentoRepository,
) {

    private val aparelhoA = "3f1c2b7e-8a4d-4e1f-9b2c-5d6e7f8a9b0c"
    private val aparelhoB = "a0b1c2d3-e4f5-4a6b-8c7d-9e0f1a2b3c4d"

    private fun ubs(): Long = requireNotNull(
        ubsRepository.saveAndFlush(
            Ubs(
                cnes = "9000101",
                nome = "UBS Teste",
                tipo = TipoUnidade.UBS,
                bairro = "Centro",
                endereco = "Rua A, 1",
                codigoIbgeMunicipio = "2704302",
                latitude = -9.5,
                longitude = -35.7,
            ),
        ).id,
    )

    private fun medicamento(): Long = requireNotNull(
        medicamentoRepository.saveAndFlush(
            Medicamento(
                catmat = "BR0267690",
                nome = "Metformina",
                concentracao = "850 mg",
                formaFarmaceutica = "Comprimido",
                categoria = "Diabetes",
            ),
        ).id,
    )

    private fun criar(aparelho: String, ubsId: Long, medicamentoId: Long) =
        mockMvc.post("/api/lista-desejos") {
            header(CABECALHO_DISPOSITIVO, aparelho)
            contentType = MediaType.APPLICATION_JSON
            content = """{"ubsId":$ubsId,"medicamentoId":$medicamentoId}"""
        }

    private fun listar(aparelho: String) =
        mockMvc.get("/api/lista-desejos") { header(CABECALHO_DISPOSITIVO, aparelho) }

    private fun aviso(aparelho: String, itemId: Long, notificar: Boolean?) =
        mockMvc.patch("/api/lista-desejos/$itemId") {
            header(CABECALHO_DISPOSITIVO, aparelho)
            contentType = MediaType.APPLICATION_JSON
            content = """{"notificar":$notificar}"""
        }

    private fun remover(aparelho: String, itemId: Long) =
        mockMvc.delete("/api/lista-desejos/$itemId") { header(CABECALHO_DISPOSITIVO, aparelho) }

    private fun extrairId(json: String): Long =
        assertNotNull(Regex("\"id\":(\\d+)").find(json)).groupValues[1].toLong()

    @Test
    fun `aparelho cria, lista, desliga o aviso e remove sem login`() {
        val unidade = ubs()
        val remedio = medicamento()

        val resposta = criar(aparelhoA, unidade, remedio).andExpect {
            status { isCreated() }
            jsonPath("$.ubsId") { value(unidade) }
            jsonPath("$.ubsNome") { value("UBS Teste") }
            jsonPath("$.medicamentoId") { value(remedio) }
            jsonPath("$.medicamentoNome") { value("Metformina") }
            jsonPath("$.medicamentoConcentracao") { value("850 mg") }
            jsonPath("$.notificar") { value(true) }
            jsonPath("$.criadoEm") { exists() }
        }.andReturn().response.contentAsString
        val itemId = extrairId(resposta)

        listar(aparelhoA).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(itemId) }
        }

        aviso(aparelhoA, itemId, false).andExpect {
            status { isOk() }
            jsonPath("$.notificar") { value(false) }
        }

        remover(aparelhoA, itemId).andExpect { status { isNoContent() } }
        listar(aparelhoA).andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `um aparelho nao ve nem altera itens de outro`() {
        val itemId = extrairId(criar(aparelhoA, ubs(), medicamento()).andReturn().response.contentAsString)

        listar(aparelhoB).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        aviso(aparelhoB, itemId, false).andExpect { status { isNotFound() } }
        remover(aparelhoB, itemId).andExpect { status { isNotFound() } }

        listar(aparelhoA).andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].notificar") { value(true) }
        }
    }

    @Test
    fun `duplicata no mesmo aparelho retorna 409, mas outro aparelho pode ter o mesmo item`() {
        val unidade = ubs()
        val remedio = medicamento()

        criar(aparelhoA, unidade, remedio).andExpect { status { isCreated() } }
        criar(aparelhoA, unidade, remedio).andExpect {
            status { isConflict() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(409) }
            jsonPath("$.title") { value("Item já cadastrado") }
        }
        criar(aparelhoB, unidade, remedio).andExpect { status { isCreated() } }
    }

    @Test
    fun `sem o cabecalho, ou fora do formato UUID, as quatro rotas retornam 400`() {
        mockMvc.get("/api/lista-desejos").andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.title") { value("Parâmetro inválido") }
        }
        mockMvc.post("/api/lista-desejos") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ubsId":1,"medicamentoId":1}"""
        }.andExpect { status { isBadRequest() } }
        mockMvc.patch("/api/lista-desejos/1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"notificar":false}"""
        }.andExpect { status { isBadRequest() } }
        mockMvc.delete("/api/lista-desejos/1").andExpect { status { isBadRequest() } }

        for (invalido in listOf("abc", "1-1-1-1-1", "", "3f1c2b7e8a4d4e1f9b2c5d6e7f8a9b0c")) {
            listar(invalido).andExpect {
                status { isBadRequest() }
                jsonPath("$.title") { value("Parâmetro inválido") }
            }
        }
    }

    @Test
    fun `ubs ou medicamento inexistente retorna 404`() {
        val unidade = ubs()
        val remedio = medicamento()

        criar(aparelhoA, 999999L, remedio).andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
        criar(aparelhoA, unidade, 999999L).andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
    }

    @Test
    fun `notificacao nula e identificadores invalidos retornam 400`() {
        criar(aparelhoA, 0, -1).andExpect { status { isBadRequest() } }
        aviso(aparelhoA, 1, null).andExpect { status { isBadRequest() } }
    }
}
