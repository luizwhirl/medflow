package br.ufal.medflow.common

import br.ufal.medflow.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers

/** Formato único das respostas paginadas (PDS-22), com banco real e dados criados pelo teste. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PaginacaoIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {

    /** Cria `quantidade` medicamentos "Paginado 01", "Paginado 02", ... e devolve os ids na ordem do nome. */
    private fun criarMedicamentos(quantidade: Int, categoria: String = "Paginação"): List<Long> =
        (1..quantidade).map { n ->
            jdbc.queryForObject(
                """
                INSERT INTO medicamento (catmat, nome, concentracao, forma_farmaceutica, categoria)
                VALUES (?, ?, '10 mg', 'comprimido', ?) RETURNING id
                """,
                Long::class.java,
                "PAG-%s-%02d".format(categoria.take(3), n),
                "Paginado %02d".format(n),
                categoria,
            )!!
        }

    @Test
    fun `busca de medicamentos devolve a pagina pedida com os totais`() {
        criarMedicamentos(25)

        mockMvc.get("/api/medicamentos") {
            param("busca", "paginado")
            param("pagina", "2")
            param("tamanho", "10")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens.length()") { value(5) }
            jsonPath("$.itens[0].nome") { value("Paginado 21") }
            jsonPath("$.itens[4].nome") { value("Paginado 25") }
            jsonPath("$.pagina") { value(2) }
            jsonPath("$.tamanho") { value(10) }
            jsonPath("$.totalItens") { value(25) }
            jsonPath("$.totalPaginas") { value(3) }
        }
    }

    @Test
    fun `sem parametros usa a primeira pagina com 20 itens`() {
        criarMedicamentos(25)

        mockMvc.get("/api/medicamentos") { param("categoria", "paginação") }.andExpect {
            status { isOk() }
            jsonPath("$.itens.length()") { value(20) }
            jsonPath("$.itens[0].nome") { value("Paginado 01") }
            jsonPath("$.pagina") { value(0) }
            jsonPath("$.tamanho") { value(Paginacao.TAMANHO_PADRAO) }
            jsonPath("$.totalItens") { value(25) }
        }
    }

    @Test
    fun `pagina alem do fim vem vazia, mas com os totais`() {
        criarMedicamentos(3)

        mockMvc.get("/api/medicamentos") {
            param("busca", "paginado")
            param("pagina", "5")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens.length()") { value(0) }
            jsonPath("$.totalItens") { value(3) }
            jsonPath("$.totalPaginas") { value(1) }
        }
    }

    @Test
    fun `estoque da UBS segue o mesmo formato, em ordem de nome do medicamento`() {
        val ubsId = jdbc.queryForObject(
            """
            INSERT INTO ubs (cnes, nome, tipo, bairro, endereco, codigo_ibge_municipio, latitude, longitude)
            VALUES ('7999922', 'UBS Paginação', 'UBS', 'Centro', 'Rua A, 1', '2704302', -9.6, -35.7) RETURNING id
            """,
            Long::class.java,
        )!!
        // Inseridos fora de ordem para conferir a ordenação por nome
        criarMedicamentos(3).reversed().forEach { medicamentoId ->
            jdbc.update(
                "INSERT INTO item_estoque (ubs_id, medicamento_id, status, quantidade) VALUES (?, ?, 'DISPONIVEL', 50)",
                ubsId,
                medicamentoId,
            )
        }

        mockMvc.get("/api/ubs/$ubsId/estoque") { param("tamanho", "2") }.andExpect {
            status { isOk() }
            jsonPath("$.itens.length()") { value(2) }
            jsonPath("$.itens[0].medicamento.nome") { value("Paginado 01") }
            jsonPath("$.itens[1].medicamento.nome") { value("Paginado 02") }
            jsonPath("$.pagina") { value(0) }
            jsonPath("$.tamanho") { value(2) }
            jsonPath("$.totalItens") { value(3) }
            jsonPath("$.totalPaginas") { value(2) }
        }
    }

    @Test
    fun `pagina e tamanho invalidos retornam 400 em problem+json`() {
        listOf(
            "/api/medicamentos" to ("pagina" to "-1"),
            "/api/medicamentos" to ("tamanho" to "0"),
            "/api/medicamentos" to ("tamanho" to "${Paginacao.TAMANHO_MAXIMO + 1}"),
            "/api/ubs/1/estoque" to ("tamanho" to "${Paginacao.TAMANHO_MAXIMO + 1}"),
        ).forEach { (url, parametro) ->
            mockMvc.get(url) { param(parametro.first, parametro.second) }.andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            }
        }
    }
}
