package br.ufal.medflow.ubs

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.estoque.StatusEstoque
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.everyItem
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

/** Testes do estoque da UBS com PostgreSQL e dados próprios. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class UbsEstoqueIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private fun criarEstoqueTeste(): Long {
        val ubsId = requireNotNull(
            jdbc.queryForObject(
                """
                insert into ubs (
                    cnes, nome, tipo, bairro, endereco,
                    codigo_ibge_municipio, latitude, longitude
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """.trimIndent(),
                Long::class.java,
                "7999901",
                "UBS Teste Integracao",
                "UBS",
                "Centro",
                "Rua Teste, 100",
                "2704302",
                -9.66,
                -35.73,
            ),
        )

        val statusItens = listOf(
            StatusEstoque.DISPONIVEL,
            StatusEstoque.DISPONIVEL,
            StatusEstoque.DISPONIVEL,
            StatusEstoque.RESERVADO,
            StatusEstoque.EM_FALTA,
        )

        statusItens.forEachIndexed { indice, status ->
            val medicamentoId = requireNotNull(
                jdbc.queryForObject(
                    """
                    insert into medicamento (
                        catmat, nome, concentracao,
                        forma_farmaceutica, categoria
                    )
                    values (?, ?, ?, ?, ?)
                    returning id
                    """.trimIndent(),
                    Long::class.java,
                    "TESTEF2${indice + 1}",
                    "Medicamento Teste ${indice + 1}",
                    "500 mg",
                    "comprimido",
                    "Teste",
                ),
            )

            jdbc.update(
                """
                insert into item_estoque (
                    ubs_id, medicamento_id, status, quantidade
                )
                values (?, ?, ?, ?)
                """.trimIndent(),
                ubsId,
                medicamentoId,
                status.name,
                if (status == StatusEstoque.EM_FALTA) 0 else 10,
            )
        }

        return ubsId
    }

    @Test
    fun `resumo confere com os dados criados pelo teste`() {
        val ubsId = criarEstoqueTeste()

        mockMvc.get("/api/ubs/$ubsId/estoque/resumo").andExpect {
            status { isOk() }
            jsonPath("$.disponivel") { value(3) }
            jsonPath("$.reservado") { value(1) }
            jsonPath("$.emFalta") { value(1) }
            jsonPath("$.itens.length()") { value(5) }
        }
    }

    @Test
    fun `filtro do resumo preserva as contagens do estoque`() {
        val ubsId = criarEstoqueTeste()

        mockMvc.get("/api/ubs/$ubsId/estoque/resumo") {
            param("status", "DISPONIVEL")
        }.andExpect {
            status { isOk() }
            jsonPath("$.disponivel") { value(3) }
            jsonPath("$.reservado") { value(1) }
            jsonPath("$.emFalta") { value(1) }
            jsonPath("$.itens.length()") { value(3) }
            jsonPath("$.itens[*].status") {
                value(everyItem(equalTo("DISPONIVEL")))
            }
        }
    }

    @Test
    fun `rota original mantem lista e aceita filtro`() {
        val ubsId = criarEstoqueTeste()

        mockMvc.get("/api/ubs/$ubsId/estoque").andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
            jsonPath("$.length()") { value(5) }
        }

        mockMvc.get("/api/ubs/$ubsId/estoque") {
            param("status", "RESERVADO")
        }.andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].status") { value("RESERVADO") }
        }
    }

    @Test
    fun `estoque de UBS inexistente retorna 404`() {
        mockMvc.get("/api/ubs/-1/estoque").andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }

        mockMvc.get("/api/ubs/-1/estoque/resumo").andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
    }
}
