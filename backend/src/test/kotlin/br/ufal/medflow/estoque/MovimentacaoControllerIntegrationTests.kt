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
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Cada teste cria as próprias UBS, medicamentos e movimentações, dentro da sua transação, então nada depende do seed.
 * UBS U1 e U2 (do mesmo medicamento M1) e M2 só na U1: o filtro por UBS e o por medicamento são exercitados de verdade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev", "demo")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MovimentacaoControllerIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private class Cenario(val u1: Long, val u2: Long, val m1: Long, val m2: Long, val itemU1M1: Long, val itemU1M2: Long, val itemU2M1: Long)

    private fun id(sql: String, vararg args: Any?) = jdbc.queryForObject(sql, Long::class.java, *args)!!

    private fun ubs(cnes: String, nome: String) = id(
        "INSERT INTO ubs (cnes, nome, tipo, bairro, endereco, codigo_ibge_municipio, latitude, longitude) " +
            "VALUES (?, ?, 'UBS', 'Centro', 'Rua A, 1', '9999999', -9.6, -35.7) RETURNING id",
        cnes, nome,
    )

    private fun medicamento(catmat: String, nome: String) = id(
        "INSERT INTO medicamento (catmat, nome, concentracao, forma_farmaceutica, categoria) VALUES (?, ?, '10 mg', 'comprimido', 'Teste') RETURNING id",
        catmat, nome,
    )

    private fun item(ubs: Long, medicamento: Long) = id(
        "INSERT INTO item_estoque (ubs_id, medicamento_id, status, quantidade) VALUES (?, ?, 'DISPONIVEL', 100) RETURNING id", ubs, medicamento,
    )

    /** Movimentação de [horasAtras] horas atrás; o tipo sai do sinal da quantidade. */
    private fun movimentacao(item: Long, quantidade: Int, horasAtras: Int, tipo: String = if (quantidade > 0) "ENTRADA" else "DISPENSACAO") =
        jdbc.update(
            "INSERT INTO movimentacao_estoque (item_estoque_id, tipo, quantidade, ocorrido_em) VALUES (?, ?, ?, NOW() - ? * INTERVAL '1 hour')",
            item, tipo, quantidade, horasAtras,
        )

    private fun cenario(): Cenario {
        val u1 = ubs("9970011", "UBS Um")
        val u2 = ubs("9970012", "UBS Dois")
        val m1 = medicamento("MOV-1", "Primeiro")
        val m2 = medicamento("MOV-2", "Segundo")
        return Cenario(u1, u2, m1, m2, item(u1, m1), item(u1, m2), item(u2, m1))
    }

    private fun movimentacoes(ubs: Long, configurar: MockHttpServletRequestDsl.() -> Unit = {}): ResultActionsDsl =
        mockMvc.get("/api/ubs/{id}/movimentacoes", ubs) { configurar() }

    @Test
    fun `lista as mais recentes primeiro com medicamento, tipo e quantidade com sinal`() {
        val c = cenario()
        movimentacao(c.itemU1M1, quantidade = 200, horasAtras = 30)
        movimentacao(c.itemU1M2, quantidade = -20, horasAtras = 1)
        movimentacao(c.itemU1M1, quantidade = 10, horasAtras = 5, tipo = "AJUSTE")
        movimentacao(c.itemU1M1, quantidade = -45, horasAtras = 10)

        movimentacoes(c.u1).andExpect {
            status { isOk() }
            jsonPath("$[*].quantidade") { value(contains(-20, 10, -45, 200)) }
            jsonPath("$[*].tipo") { value(contains("DISPENSACAO", "AJUSTE", "DISPENSACAO", "ENTRADA")) }
            jsonPath("$[0].medicamentoId") { value(c.m2) }
            jsonPath("$[0].medicamentoNome") { value("Segundo") }
            jsonPath("$[1].medicamentoNome") { value("Primeiro") }
            jsonPath("$[0].ocorridoEm") { isNotEmpty() }
        }
    }

    @Test
    fun `sem limite traz exatamente as 20 mais recentes`() {
        val c = cenario()
        // 25 movimentações: a de i horas atrás tem quantidade i, então a mais recente (1) vem primeiro
        (1..25).forEach { movimentacao(c.itemU1M1, quantidade = it, horasAtras = it) }

        movimentacoes(c.u1).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(20) }
            jsonPath("$[0].quantidade") { value(1) }
            jsonPath("$[19].quantidade") { value(20) }
        }
    }

    @Test
    fun `limite escolhe quantas trazer, de 1 a 100`() {
        val c = cenario()
        (1..25).forEach { movimentacao(c.itemU1M1, quantidade = it, horasAtras = it) }

        movimentacoes(c.u1) { param("limite", "1") }.andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].quantidade") { value(1) }
        }
        movimentacoes(c.u1) { param("limite", "100") }.andExpect { jsonPath("$.length()") { value(25) } }
    }

    @Test
    fun `filtra pelo medicamento e so da UBS pedida`() {
        val c = cenario()
        movimentacao(c.itemU1M1, quantidade = 11, horasAtras = 3)
        movimentacao(c.itemU1M1, quantidade = -5, horasAtras = 8)
        movimentacao(c.itemU1M2, quantidade = 99, horasAtras = 4) // outro medicamento, mesma UBS
        movimentacao(c.itemU2M1, quantidade = 77, horasAtras = 2) // mesmo medicamento, outra UBS

        movimentacoes(c.u1) { param("medicamentoId", c.m1.toString()) }.andExpect {
            status { isOk() }
            jsonPath("$[*].quantidade") { value(contains(11, -5)) }
            jsonPath("$[*].medicamentoNome") { value(everyItem(equalTo("Primeiro"))) }
        }
        movimentacoes(c.u2) { param("medicamentoId", c.m1.toString()) }.andExpect {
            jsonPath("$[*].quantidade") { value(contains(77)) }
        }
    }

    @Test
    fun `medicamento que existe mas nao tem movimentacao na UBS responde 200 com lista vazia`() {
        val c = cenario()
        movimentacao(c.itemU1M1, quantidade = 10, horasAtras = 1)

        movimentacoes(c.u1) { param("medicamentoId", c.m2.toString()) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        movimentacoes(c.u2).andExpect { jsonPath("$.length()") { value(0) } } // UBS sem nenhuma movimentação
    }

    @Test
    fun `UBS ou medicamento inexistente retorna 404 no formato problem json`() {
        val c = cenario()

        mockMvc.get("/api/ubs/999999/movimentacoes").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
        movimentacoes(c.u1) { param("medicamentoId", "999999") }.andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith("application/problem+json") }
        }
    }

    @Test
    fun `limite fora de 1 a 100 retorna 400 no formato problem json`() {
        val c = cenario()

        listOf("0", "-1", "101", "abc").forEach { limite ->
            movimentacoes(c.u1) { param("limite", limite) }.andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith("application/problem+json") }
            }
        }
    }
}
