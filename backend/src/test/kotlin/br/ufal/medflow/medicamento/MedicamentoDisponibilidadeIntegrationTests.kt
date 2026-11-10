package br.ufal.medflow.medicamento

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.estoque.StatusEstoque
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

/** Testes do resumo de disponibilidade com PostgreSQL e dados próprios. */
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

    private fun criarMedicamento(): Long =
        requireNotNull(
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
                "TESTEF4REV",
                "Medicamento Teste F4",
                "500 mg",
                "comprimido",
                "Teste",
            ),
        )

    private fun criarUbs(
        cnes: String,
        ibge: String,
    ): Long =
        requireNotNull(
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
                cnes,
                "UBS Teste $cnes",
                "UBS",
                "Centro",
                "Rua Teste, 100",
                ibge,
                -9.66,
                -35.73,
            ),
        )

    private fun criarItem(
        ubsId: Long,
        medicamentoId: Long,
        status: StatusEstoque,
        quantidade: Int,
    ) {
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
            quantidade,
        )
    }

    private fun criarCenario(): Long {
        val medicamentoId = criarMedicamento()

        val ubsA1 = criarUbs("7980001", "9999901")
        val ubsA2 = criarUbs("7980002", "9999901")
        val ubsA3 = criarUbs("7980003", "9999901")
        criarUbs("7980004", "9999901")

        val ubsB1 = criarUbs("7981001", "9999902")
        val ubsB2 = criarUbs("7981002", "9999902")

        criarItem(ubsA1, medicamentoId, StatusEstoque.DISPONIVEL, 20)
        criarItem(ubsA2, medicamentoId, StatusEstoque.RESERVADO, 10)
        criarItem(ubsA3, medicamentoId, StatusEstoque.EM_FALTA, 0)
        criarItem(ubsB1, medicamentoId, StatusEstoque.DISPONIVEL, 15)
        criarItem(ubsB2, medicamentoId, StatusEstoque.DISPONIVEL, 0)

        return medicamentoId
    }

    @Test
    fun `resumo conta apenas estoque disponivel por municipio`() {
        val medicamentoId = criarCenario()

        mockMvc.get("/api/medicamentos/$medicamentoId/disponibilidade/resumo") {
            param("ibge", "9999901")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalUnidades") { value(4) }
            jsonPath("$.unidadesDisponiveis") { value(1) }
        }

        mockMvc.get("/api/medicamentos/$medicamentoId/disponibilidade/resumo") {
            param("ibge", "9999902")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalUnidades") { value(2) }
            jsonPath("$.unidadesDisponiveis") { value(1) }
        }
    }

    @Test
    fun `resumo sem filtro considera todos os municipios`() {
        val totalInicial = requireNotNull(
            jdbc.queryForObject(
                "select count(*) from ubs",
                Long::class.java,
            ),
        )

        val medicamentoId = criarCenario()

        mockMvc.get("/api/medicamentos/$medicamentoId/disponibilidade/resumo").andExpect {
            status { isOk() }
            jsonPath("$.totalUnidades") { value((totalInicial + 6).toInt()) }
            jsonPath("$.unidadesDisponiveis") { value(2) }
        }
    }

    @Test
    fun `resumo normaliza codigo IBGE de seis digitos`() {
        val medicamentoId = criarMedicamento()

        val totalInicial = requireNotNull(
            jdbc.queryForObject(
                "select count(*) from ubs where codigo_ibge_municipio = ?",
                Long::class.java,
                "2704302",
            ),
        )

        val ubsId = criarUbs("7983001", "2704302")
        criarItem(ubsId, medicamentoId, StatusEstoque.DISPONIVEL, 5)

        for (codigo in listOf("270430", "2704302")) {
            mockMvc.get("/api/medicamentos/$medicamentoId/disponibilidade/resumo") {
                param("ibge", codigo)
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalUnidades") { value((totalInicial + 1).toInt()) }
                jsonPath("$.unidadesDisponiveis") { value(1) }
            }
        }
    }

    @Test
    fun `resumo retorna 400 para codigo IBGE invalido`() {
        val medicamentoId = criarMedicamento()

        for (codigo in listOf("27043", "abc", "27043022")) {
            mockMvc.get("/api/medicamentos/$medicamentoId/disponibilidade/resumo") {
                param("ibge", codigo)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.title") { value("Parâmetro inválido") }
                jsonPath("$.status") { value(400) }
            }
        }
    }

    @Test
    fun `medicamento sem estoque retorna zero unidades disponiveis`() {
        val medicamentoId = criarMedicamento()
        criarUbs("7982001", "9999903")

        mockMvc.get("/api/medicamentos/$medicamentoId/disponibilidade/resumo") {
            param("ibge", "9999903")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalUnidades") { value(1) }
            jsonPath("$.unidadesDisponiveis") { value(0) }
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
