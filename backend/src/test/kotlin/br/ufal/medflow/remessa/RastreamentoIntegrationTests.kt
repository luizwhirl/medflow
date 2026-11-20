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
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

/** Perfil "dev": usa o seed de remessas (V1006) e cria remessas extras só dentro de cada teste. */
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
    private val metformina = "BR0267690"
    private val losartana = "BR0268856"
    private val dipirona = "BR0267543"

    private fun idUbs(cnes: String) = jdbc.queryForObject("SELECT id FROM ubs WHERE cnes = ?", Long::class.java, cnes)!!

    private fun idMedicamento(catmat: String) =
        jdbc.queryForObject("SELECT id FROM medicamento WHERE catmat = ?", Long::class.java, catmat)!!

    /** Cria uma remessa com os eventos das primeiras [etapas] etapas e devolve o id. */
    private fun criarRemessa(cnes: String, catmat: String, quantidade: Int, previsaoEmDias: Int, diasAtras: Int, etapas: Int): Long {
        val id = jdbc.queryForObject(
            "INSERT INTO remessa (ubs_id, medicamento_id, quantidade, previsao_chegada, criado_em) " +
                "VALUES (?, ?, ?, CURRENT_DATE + ?, NOW() - ? * INTERVAL '1 day') RETURNING id",
            Long::class.java,
            idUbs(cnes),
            idMedicamento(catmat),
            quantidade,
            previsaoEmDias,
            diasAtras,
        )!!
        EtapaRemessa.entries.take(etapas).forEachIndexed { i, etapa ->
            jdbc.update(
                "INSERT INTO remessa_evento (remessa_id, etapa, ocorrido_em) VALUES (?, ?, NOW() - ? * INTERVAL '1 day' + ? * INTERVAL '1 hour')",
                id,
                etapa.name,
                diasAtras,
                i,
            )
        }
        return id
    }

    private fun rastrear(catmat: String, cnes: String) =
        mockMvc.get("/api/medicamentos/{id}/rastreamento", idMedicamento(catmat)) { param("ubsId", idUbs(cnes).toString()) }

    @Test
    fun `remessa em transporte traz etapa atual, previsao e as cinco etapas com data so ate a atual`() {
        val previsao = jdbc.queryForObject(
            "SELECT previsao_chegada FROM remessa WHERE ubs_id = ? AND medicamento_id = ?",
            LocalDate::class.java,
            idUbs("9000101"),
            idMedicamento(metformina),
        )

        rastrear(metformina, "9000101").andExpect {
            status { isOk() }
            jsonPath("$.remessa.etapaAtual") { value("EM_TRANSPORTE") }
            jsonPath("$.remessa.concluida") { value(false) }
            jsonPath("$.remessa.quantidade") { value(300) }
            jsonPath("$.remessa.previsaoChegada") { value(previsao.toString()) }
            jsonPath("$.remessa.linhaDoTempo.length()") { value(5) }
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
        rastrear(losartana, "9000104").andExpect {
            status { isOk() }
            jsonPath("$.remessa.etapaAtual") { value("DISPONIVEL") }
            jsonPath("$.remessa.concluida") { value(true) }
            jsonPath("$.remessa.linhaDoTempo[4].ocorridoEm") { isNotEmpty() }
        }
    }

    @Test
    fun `sem remessa para a UBS a resposta e 200 com remessa nula`() {
        rastrear(dipirona, "9000102").andExpect {
            status { isOk() }
            jsonPath("$.remessa") { value(nullValue()) }
            jsonPath("$.ubsId") { value(idUbs("9000102")) }
        }
    }

    @Test
    fun `entre as em andamento vale a que chega primeiro`() {
        criarRemessa("9000102", dipirona, quantidade = 10, previsaoEmDias = 10, diasAtras = 1, etapas = 1)
        val maisCedo = criarRemessa("9000102", dipirona, quantidade = 20, previsaoEmDias = 3, diasAtras = 2, etapas = 2)

        rastrear(dipirona, "9000102").andExpect {
            jsonPath("$.remessa.id") { value(maisCedo) }
            jsonPath("$.remessa.etapaAtual") { value("SEPARADO_CAF") }
        }
    }

    @Test
    fun `remessa em andamento vale mais que uma concluida mais recente`() {
        // seed: a de Losartana na UBS 9000104 já chegou. Esta, mais antiga, ainda está em andamento.
        val andamento = criarRemessa("9000104", losartana, quantidade = 50, previsaoEmDias = 1, diasAtras = 30, etapas = 3)

        rastrear(losartana, "9000104").andExpect {
            jsonPath("$.remessa.id") { value(andamento) }
            jsonPath("$.remessa.concluida") { value(false) }
        }
    }

    @Test
    fun `sem nenhuma em andamento vale a concluida mais recente`() {
        criarRemessa("9000102", dipirona, quantidade = 5, previsaoEmDias = -20, diasAtras = 25, etapas = 5)
        val recente = criarRemessa("9000102", dipirona, quantidade = 8, previsaoEmDias = -2, diasAtras = 5, etapas = 5)

        rastrear(dipirona, "9000102").andExpect {
            jsonPath("$.remessa.id") { value(recente) }
            jsonPath("$.remessa.quantidade") { value(8) }
        }
    }

    @Test
    fun `medicamento ou UBS inexistente retorna 404`() {
        mockMvc.get("/api/medicamentos/999999/rastreamento") { param("ubsId", idUbs("9000101").toString()) }
            .andExpect { status { isNotFound() } }
        mockMvc.get("/api/medicamentos/{id}/rastreamento", idMedicamento(metformina)) { param("ubsId", "999999") }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `ubsId ausente ou invalido retorna 400`() {
        val id = idMedicamento(metformina)

        mockMvc.get("/api/medicamentos/{id}/rastreamento", id).andExpect { status { isBadRequest() } }
        mockMvc.get("/api/medicamentos/{id}/rastreamento", id) { param("ubsId", "0") }.andExpect { status { isBadRequest() } }
        mockMvc.get("/api/medicamentos/{id}/rastreamento", id) { param("ubsId", "abc") }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `endpoint e publico`() {
        // sem token: leitura de medicamentos é pública
        rastrear(metformina, "9000101").andExpect { status { isOk() } }
    }
}
