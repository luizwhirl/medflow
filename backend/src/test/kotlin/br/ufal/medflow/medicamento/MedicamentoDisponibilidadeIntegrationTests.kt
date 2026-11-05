package br.ufal.medflow.medicamento

import br.ufal.medflow.TestcontainersConfiguration
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

/** Testes do resumo de disponibilidade com PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MedicamentoDisponibilidadeIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @Test
    fun `resumo da metformina confere com os dados do seed`() {
        val medicamentoId = requireNotNull(
            jdbc.queryForObject(
                "select id from medicamento where catmat = ?",
                Long::class.java,
                "BR0267690",
            ),
        )

        mockMvc.get("/api/medicamentos/$medicamentoId/disponibilidade/resumo").andExpect {
            status { isOk() }
            jsonPath("$.totalUnidades") { value(7) }
            jsonPath("$.unidadesComItem") { value(6) }
        }
    }

    @Test
    fun `medicamento sem estoque retorna zero unidades com item`() {
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
                "TESTEF4",
                "Medicamento Teste F4",
                "500 mg",
                "comprimido",
                "Teste",
            ),
        )

        val totalUnidades = requireNotNull(
            jdbc.queryForObject(
                "select count(*) from ubs",
                Long::class.java,
            ),
        )

        mockMvc.get("/api/medicamentos/$medicamentoId/disponibilidade/resumo").andExpect {
            status { isOk() }
            jsonPath("$.totalUnidades") { value(totalUnidades.toInt()) }
            jsonPath("$.unidadesComItem") { value(0) }
        }
    }

    @Test
    fun `resumo de medicamento inexistente retorna 404`() {
        mockMvc.get("/api/medicamentos/-1/disponibilidade/resumo").andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
    }
}
