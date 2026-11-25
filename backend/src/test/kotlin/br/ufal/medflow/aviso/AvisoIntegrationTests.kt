package br.ufal.medflow.aviso

import br.ufal.medflow.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** Contrato de avisos públicos por UUID, com isolamento entre dispositivos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AvisoIntegrationTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val avisos: AvisoRepository,
) {
    private val a = UUID.fromString("15da7427-97f6-4bf1-9d99-004af964e951")
    private val b = UUID.fromString("dcff9dbc-5ef6-4999-b880-83ad4e14e7db")

    private fun criar(id: UUID, chave: String, lido: Boolean = false, criadoEm: Instant = Instant.now()): Aviso =
        avisos.saveAndFlush(
            Aviso(
                dispositivoId = id,
                tipo = TipoAviso.MEDICAMENTO_DISPONIVEL,
                titulo = "Medicamento disponível",
                texto = "Exemplo",
                lido = lido,
                criadoEm = criadoEm,
                chaveEvento = chave,
            ),
        )

    @Test
    fun `lista somente avisos do dispositivo sem JWT`() {
        val antigo = criar(a, "api-antigo", criadoEm = Instant.parse("2026-11-19T10:00:00Z"))
        val novo = criar(a, "api-novo", criadoEm = Instant.parse("2026-11-19T20:00:00Z"))
        criar(b, "api-outro", criadoEm = Instant.parse("2026-11-20T20:00:00Z"))

        mvc.get("/api/avisos") { header("X-Dispositivo-Id", a.toString()) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].id") { value(novo.id!!.toInt()) }
            jsonPath("$[1].id") { value(antigo.id!!.toInt()) }
        }
        mvc.get("/api/avisos") { header("X-Dispositivo-Id", b.toString()) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }

    @Test
    fun `limite da lista nao altera contador e marcacao de leitura`() {
        (1..60).forEach { numero ->
            criar(a, "api-limite-$numero", lido = numero <= 5, criadoEm = Instant.parse("2026-11-19T20:00:00Z"))
        }
        criar(b, "api-limite-outro")

        mvc.get("/api/avisos") { header("X-Dispositivo-Id", a.toString()) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(50) }
        }
        mvc.get("/api/avisos/nao-lidos/total") { header("X-Dispositivo-Id", a.toString()) }.andExpect {
            status { isOk() }
            jsonPath("$.naoLidos") { value(55) }
        }
        mvc.patch("/api/avisos/lidas") { header("X-Dispositivo-Id", a.toString()) }.andExpect {
            status { isOk() }
            jsonPath("$.atualizados") { value(55) }
        }
        assertEquals(0L, avisos.countByDispositivoIdAndLidoFalse(a))
        assertEquals(1L, avisos.countByDispositivoIdAndLidoFalse(b))
    }

    @Test
    fun `UUID ausente ou invalido devolve 400`() {
        mvc.get("/api/avisos").andExpect { status { isBadRequest() } }
        mvc.get("/api/avisos") { header("X-Dispositivo-Id", "123") }.andExpect {
            status { isBadRequest() }
        }
        mvc.patch("/api/avisos/lidas") { header("X-Dispositivo-Id", "1-1-1-1-1") }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `aparelho sem historico retorna vazio`() {
        mvc.get("/api/avisos") { header("X-Dispositivo-Id", a.toString()) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        mvc.get("/api/avisos/nao-lidos/total") { header("X-Dispositivo-Id", a.toString()) }.andExpect {
            status { isOk() }
            jsonPath("$.naoLidos") { value(0) }
        }
    }
}
