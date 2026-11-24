package br.ufal.medflow.remessa

import br.ufal.medflow.TestcontainersConfiguration
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

/**
 * Cada teste cria a própria UBS, o próprio medicamento e as próprias remessas, dentro da sua transação,
 * então nada depende do que o seed tem.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class RastreamentoIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val hoje: LocalDate = LocalDate.now()

    private fun id(sql: String, vararg args: Any?) = jdbc.queryForObject(sql, Long::class.java, *args)!!

    private fun ubs(cnes: String = "9970001") = id(
        "INSERT INTO ubs (cnes, nome, tipo, bairro, endereco, codigo_ibge_municipio, latitude, longitude) " +
            "VALUES (?, 'UBS Rastreio', 'UBS', 'Centro', 'Rua A, 1', '9999999', -9.6, -35.7) RETURNING id",
        cnes,
    )

    private fun medicamento(catmat: String = "RASTREIO-1") = id(
        "INSERT INTO medicamento (catmat, nome, concentracao, forma_farmaceutica, categoria) " +
            "VALUES (?, 'Rastreável', '10 mg', 'comprimido', 'Teste') RETURNING id",
        catmat,
    )

    /** Cria a remessa com os eventos das primeiras [etapas] etapas, uma hora depois da outra, a partir de [criadaHaDias]. */
    private fun remessa(ubs: Long, medicamento: Long, quantidade: Int, previsaoEmDias: Long, criadaHaDias: Int, etapas: Int): Long {
        val remessa = id(
            "INSERT INTO remessa (ubs_id, medicamento_id, quantidade, previsao_chegada, criado_em) " +
                "VALUES (?, ?, ?, ?, NOW() - ? * INTERVAL '1 day') RETURNING id",
            ubs, medicamento, quantidade, hoje.plusDays(previsaoEmDias), criadaHaDias,
        )
        EtapaRemessa.entries.take(etapas).forEachIndexed { i, etapa ->
            jdbc.update(
                "INSERT INTO remessa_evento (remessa_id, etapa, ocorrido_em) VALUES (?, ?, NOW() - ? * INTERVAL '1 day' + ? * INTERVAL '1 hour')",
                remessa, etapa.name, criadaHaDias, i,
            )
        }
        return remessa
    }

    private fun rastrear(medicamento: Long, ubs: Long): ResultActionsDsl =
        mockMvc.get("/api/medicamentos/{id}/rastreamento", medicamento) { param("ubsId", ubs.toString()) }

    @Test
    fun `remessa em transporte traz etapa atual, previsao e as cinco etapas com data so ate a atual`() {
        val (u, m) = ubs() to medicamento()
        remessa(u, m, quantidade = 300, previsaoEmDias = 2, criadaHaDias = 3, etapas = 3)

        rastrear(m, u).andExpect {
            status { isOk() }
            jsonPath("$.medicamentoId") { value(m) }
            jsonPath("$.ubsId") { value(u) }
            jsonPath("$.remessa.etapaAtual") { value("EM_TRANSPORTE") }
            jsonPath("$.remessa.concluida") { value(false) }
            jsonPath("$.remessa.quantidade") { value(300) }
            jsonPath("$.remessa.previsaoChegada") { value(hoje.plusDays(2).toString()) }
            jsonPath("$.remessa.linhaDoTempo[*].etapa") {
                value(contains("PEDIDO", "SEPARADO_CAF", "EM_TRANSPORTE", "RECEBIDO", "DISPONIVEL"))
            }
            jsonPath("$.remessa.linhaDoTempo[0].ocorridoEm") { isNotEmpty() }
            jsonPath("$.remessa.linhaDoTempo[2].ocorridoEm") { isNotEmpty() }
            jsonPath("$.remessa.linhaDoTempo[3].ocorridoEm") { value(nullValue()) }
            jsonPath("$.remessa.linhaDoTempo[4].ocorridoEm") { value(nullValue()) }
        }
    }

    @Test
    fun `remessa que ja esta disponivel vem concluida com todas as etapas datadas`() {
        val (u, m) = ubs() to medicamento()
        remessa(u, m, quantidade = 120, previsaoEmDias = -1, criadaHaDias = 6, etapas = 5)

        rastrear(m, u).andExpect {
            status { isOk() }
            jsonPath("$.remessa.etapaAtual") { value("DISPONIVEL") }
            jsonPath("$.remessa.concluida") { value(true) }
            jsonPath("$.remessa.linhaDoTempo[4].ocorridoEm") { isNotEmpty() }
        }
    }

    @Test
    fun `sem remessa para a UBS a resposta e 200 com remessa nula`() {
        val (u, m) = ubs() to medicamento()
        val outraUbs = ubs("9970002")
        remessa(outraUbs, m, quantidade = 10, previsaoEmDias = 1, criadaHaDias = 1, etapas = 1) // de outra UBS: não conta

        rastrear(m, u).andExpect {
            status { isOk() }
            jsonPath("$.remessa") { value(nullValue()) }
            jsonPath("$.ubsId") { value(u) }
        }
    }

    @Test
    fun `entre as em andamento vale a que chega primeiro, mesmo sendo a mais antiga`() {
        val (u, m) = ubs() to medicamento()
        remessa(u, m, quantidade = 10, previsaoEmDias = 10, criadaHaDias = 1, etapas = 1)
        val maisCedo = remessa(u, m, quantidade = 20, previsaoEmDias = 3, criadaHaDias = 2, etapas = 2)

        rastrear(m, u).andExpect {
            jsonPath("$.remessa.id") { value(maisCedo) }
            jsonPath("$.remessa.etapaAtual") { value("SEPARADO_CAF") }
        }
    }

    @Test
    fun `entre as em andamento vale a que chega primeiro, mesmo sendo a mais recente`() {
        val (u, m) = ubs() to medicamento()
        remessa(u, m, quantidade = 10, previsaoEmDias = 10, criadaHaDias = 5, etapas = 1)
        val maisCedo = remessa(u, m, quantidade = 20, previsaoEmDias = 3, criadaHaDias = 1, etapas = 1)

        rastrear(m, u).andExpect { jsonPath("$.remessa.id") { value(maisCedo) } }
    }

    @Test
    fun `remessa em andamento vale mais que uma concluida mais recente`() {
        val (u, m) = ubs() to medicamento()
        remessa(u, m, quantidade = 50, previsaoEmDias = -3, criadaHaDias = 2, etapas = 5) // concluída e mais recente
        val andamento = remessa(u, m, quantidade = 60, previsaoEmDias = 1, criadaHaDias = 30, etapas = 3)

        rastrear(m, u).andExpect {
            jsonPath("$.remessa.id") { value(andamento) }
            jsonPath("$.remessa.concluida") { value(false) }
        }
    }

    @Test
    fun `sem nenhuma em andamento vale a concluida mais recente`() {
        val (u, m) = ubs() to medicamento()
        remessa(u, m, quantidade = 5, previsaoEmDias = -20, criadaHaDias = 5, etapas = 5) // mais recente, previsão mais antiga
        remessa(u, m, quantidade = 8, previsaoEmDias = -2, criadaHaDias = 25, etapas = 5)

        rastrear(m, u).andExpect { jsonPath("$.remessa.quantidade") { value(5) } }
    }

    @Test
    fun `remessa sem nenhum evento sai com a etapa PEDIDO e a linha do tempo sem datas`() {
        val (u, m) = ubs() to medicamento()
        remessa(u, m, quantidade = 7, previsaoEmDias = 4, criadaHaDias = 1, etapas = 0)

        rastrear(m, u).andExpect {
            jsonPath("$.remessa.etapaAtual") { value("PEDIDO") }
            jsonPath("$.remessa.concluida") { value(false) }
            jsonPath("$.remessa.linhaDoTempo[0].ocorridoEm") { value(nullValue()) }
        }
    }

    @Test
    fun `medicamento ou UBS inexistente retorna 404 no formato problem json`() {
        val (u, m) = ubs() to medicamento()

        mockMvc.get("/api/medicamentos/999999/rastreamento") { param("ubsId", u.toString()) }.andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
        rastrear(m, 999999).andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith("application/problem+json") }
        }
    }

    @Test
    fun `ubsId ausente ou invalido retorna 400 no formato problem json`() {
        val m = medicamento()

        mockMvc.get("/api/medicamentos/{id}/rastreamento", m).andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith("application/problem+json") }
        }
        for (valor in listOf("0", "abc")) {
            mockMvc.get("/api/medicamentos/{id}/rastreamento", m) { param("ubsId", valor) }.andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith("application/problem+json") }
            }
        }
    }

    @Test
    fun `endpoint e publico`() {
        val (u, m) = ubs() to medicamento()
        rastrear(m, u).andExpect { status { isOk() } } // sem token
    }
}
