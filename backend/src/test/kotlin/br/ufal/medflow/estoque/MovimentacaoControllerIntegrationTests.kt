package br.ufal.medflow.estoque

import br.ufal.medflow.TestcontainersConfiguration
import org.hamcrest.Matchers.contains
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

/** Perfil "dev": usa o seed de movimentações (V1007). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MovimentacaoControllerIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private fun idUbs(cnes: String) = jdbc.queryForObject("SELECT id FROM ubs WHERE cnes = ?", Long::class.java, cnes)!!

    private fun idMedicamento(catmat: String) =
        jdbc.queryForObject("SELECT id FROM medicamento WHERE catmat = ?", Long::class.java, catmat)!!

    private fun movimentacoes(cnes: String, configurar: org.springframework.test.web.servlet.MockHttpServletRequestDsl.() -> Unit = {}) =
        mockMvc.get("/api/ubs/{id}/movimentacoes", idUbs(cnes)) { configurar() }

    @Test
    fun `lista as mais recentes primeiro com medicamento, tipo e quantidade com sinal`() {
        // seed, UBS 9000101, do mais recente: Losartana -20 (1 dia), Losartana +10 ajuste (5), Dipirona -200 (6)
        movimentacoes("9000101") { param("limite", "3") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].medicamentoNome") { value("Losartana potássica") }
            jsonPath("$[0].tipo") { value("DISPENSACAO") }
            jsonPath("$[0].quantidade") { value(-20) }
            jsonPath("$[1].tipo") { value("AJUSTE") }
            jsonPath("$[1].quantidade") { value(10) }
            jsonPath("$[2].medicamentoNome") { value("Dipirona") }
            jsonPath("$[2].quantidade") { value(-200) }
            jsonPath("$[0].ocorridoEm") { isNotEmpty() }
        }
    }

    @Test
    fun `sem limite traz ate 20 movimentacoes`() {
        movimentacoes("9000101").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.greaterThanOrEqualTo(8), org.hamcrest.Matchers.lessThanOrEqualTo(20))) }
        }
    }

    @Test
    fun `filtra pelo medicamento`() {
        // Losartana na UBS 9000101: -20 (1 dia), +10 ajuste (5), -30 (9), +300 entrada (18)
        movimentacoes("9000101") { param("medicamentoId", idMedicamento("BR0268856").toString()) }.andExpect {
            status { isOk() }
            jsonPath("$[*].quantidade") { value(contains(-20, 10, -30, 300)) }
            jsonPath("$[*].medicamentoNome") { value(everyItem(equalTo("Losartana potássica"))) }
        }
    }

    @Test
    fun `movimentacoes de outra UBS nao aparecem`() {
        // 9000104 só tem movimentações de Metformina
        movimentacoes("9000104") { param("limite", "100") }.andExpect {
            status { isOk() }
            jsonPath("$[*].medicamentoNome") { value(everyItem(equalTo("Metformina"))) }
            jsonPath("$.length()") { value(3) }
        }
    }

    @Test
    fun `UBS sem movimentacoes devolve lista vazia`() {
        movimentacoes("9000107").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `UBS ou medicamento inexistente retorna 404`() {
        mockMvc.get("/api/ubs/999999/movimentacoes").andExpect { status { isNotFound() } }
        movimentacoes("9000101") { param("medicamentoId", "999999") }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `limite fora de 1 a 100 retorna 400`() {
        listOf("0", "-1", "101", "abc").forEach { limite ->
            movimentacoes("9000101") { param("limite", limite) }.andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `limite no maximo e aceito`() {
        movimentacoes("9000101") { param("limite", "100") }.andExpect { status { isOk() } }
        movimentacoes("9000101") { param("limite", "1") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }
}
