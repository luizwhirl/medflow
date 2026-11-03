package br.ufal.medflow.ubs

import br.ufal.medflow.TestcontainersConfiguration
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
import org.testcontainers.junit.jupiter.Testcontainers

/** Testes do estoque da UBS com PostgreSQL e dados do seed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class UbsEstoqueIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private fun idUbsBeneditoBentes(): Long =
        requireNotNull(
            jdbc.queryForObject(
                "SELECT id FROM ubs WHERE cnes = ?",
                Long::class.java,
                "9000101",
            ),
        )

    @Test
    fun `resumo do estoque confere com os dados do seed`() {
        val ubsId = idUbsBeneditoBentes()

        mockMvc.get("/api/ubs/$ubsId/estoque").andExpect {
            status { isOk() }
            jsonPath("$.disponivel") { value(3) }
            jsonPath("$.reservado") { value(1) }
            jsonPath("$.emFalta") { value(1) }
            jsonPath("$.itens.length()") { value(5) }
        }
    }

    @Test
    fun `filtro por status preserva as contagens do estoque`() {
        val ubsId = idUbsBeneditoBentes()

        mockMvc.get("/api/ubs/$ubsId/estoque") {
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
    fun `estoque de UBS inexistente retorna 404`() {
        mockMvc.get("/api/ubs/999999/estoque").andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
    }
}
