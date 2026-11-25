package br.ufal.medflow.busca

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
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/** Cada teste cria a própria UBS e o próprio medicamento, dentro da sua transação, então nada depende do seed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ContagemBuscaIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val contagemBuscaService: ContagemBuscaService,
) {
    private val hoje: LocalDate = LocalDate.now(ZoneId.of("America/Maceio"))

    private fun id(sql: String, vararg args: Any?) = jdbc.queryForObject(sql, Long::class.java, *args)!!

    private fun ubs() = id(
        "INSERT INTO ubs (cnes, nome, tipo, bairro, endereco, codigo_ibge_municipio, latitude, longitude) " +
            "VALUES ('9960001', 'UBS Busca', 'UBS', 'Centro', 'Rua A, 1', '9999999', -9.6, -35.7) RETURNING id",
    )

    private fun medicamento() = id(
        "INSERT INTO medicamento (catmat, nome, concentracao, forma_farmaceutica, categoria) " +
            "VALUES ('BUSCA-1', 'Procurado', '10 mg', 'comprimido', 'Teste') RETURNING id",
    )

    private fun registrar(medicamento: Long, corpo: String? = null) = mockMvc.post("/api/medicamentos/$medicamento/buscas") {
        if (corpo != null) {
            contentType = MediaType.APPLICATION_JSON
            content = corpo
        }
    }

    /** Linhas de contagem do medicamento: (dia, ubs_id, total). */
    private fun contagens(medicamento: Long) = jdbc.query(
        "SELECT dia, ubs_id, total FROM contagem_busca WHERE medicamento_id = ? ORDER BY ubs_id NULLS FIRST",
        { rs, _ -> Triple(rs.getObject("dia", LocalDate::class.java), rs.getObject("ubs_id") as Long?, rs.getInt("total")) },
        medicamento,
    )

    @Test
    fun `soma as buscas do dia numa linha por UBS, sem login e sem guardar mais nada`() {
        val u = ubs()
        val m = medicamento()

        registrar(m, """{"ubsId": $u}""").andExpect { status { isNoContent() } }
        registrar(m, """{"ubsId": $u}""").andExpect { status { isNoContent() } }
        registrar(m).andExpect { status { isNoContent() } } // sem corpo: quem ainda não escolheu UBS
        registrar(m, "{}").andExpect { status { isNoContent() } }

        assertEquals(listOf(Triple(hoje, null, 2), Triple(hoje, u, 2)), contagens(m))
    }

    @Test
    fun `medicamento ou UBS que nao existem respondem 404, e ids invalidos 400`() {
        val m = medicamento()

        registrar(999_999).andExpect { status { isNotFound() } }
        registrar(m, """{"ubsId": 999999}""").andExpect { status { isNotFound() } }
        registrar(m, """{"ubsId": 0}""").andExpect { status { isBadRequest() } }
        registrar(0).andExpect { status { isBadRequest() } }
        assertEquals(emptyList(), contagens(m))
    }

    @Test
    fun `a limpeza apaga so os dias de mais de 12 meses`() {
        val m = medicamento()
        val limite = hoje.minusMonths(12)
        for (dia in listOf(limite.minusDays(1), limite, hoje)) {
            jdbc.update("INSERT INTO contagem_busca (dia, medicamento_id, total) VALUES (?, ?, 1)", dia, m)
        }

        contagemBuscaService.apagarAntigas()

        assertEquals(listOf(limite, hoje), contagens(m).map { it.first }.sorted())
    }
}
