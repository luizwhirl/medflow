package br.ufal.medflow.aviso

import br.ufal.medflow.auth.SecurityConfig
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** API de avisos, sem banco, com a segurança real (rota pública). */
@WebMvcTest(AvisoController::class)
@Import(SecurityConfig::class)
class AvisoControllerTest(@Autowired private val mockMvc: MockMvc) {

    @MockitoBean
    private lateinit var avisoService: AvisoService

    @Test
    fun `e publica e repassa a UBS e os medicamentos sem repeticao`() {
        given(avisoService.listar(1L, setOf(3L, 5L))).willReturn(
            listOf(AvisoResponse(id = "disponivel-1-3", tipo = TipoAviso.DISPONIVEL, ubsId = 1L, ubsNome = "UBS Centro")),
        )

        mockMvc.get("/api/avisos") {
            param("ubsId", "1")
            param("medicamentoIds", "3,5,3")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value("disponivel-1-3") }
            jsonPath("$[0].tipo") { value("DISPONIVEL") }
            jsonPath("$[0].etapa") { doesNotExist() }
        }

        verify(avisoService).listar(1L, setOf(3L, 5L))
    }

    @Test
    fun `sem medicamentos ainda devolve os avisos da UBS`() {
        given(avisoService.listar(1L, emptySet())).willReturn(emptyList())
        mockMvc.get("/api/avisos") { param("ubsId", "1") }.andExpect { status { isOk() } }
        verify(avisoService).listar(1L, emptySet())
    }

    @Test
    fun `parametros invalidos retornam 400 sem chamar o servico`() {
        mockMvc.get("/api/avisos").andExpect { status { isBadRequest() } }
        mockMvc.get("/api/avisos") { param("ubsId", "0") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("Parâmetro inválido") }
        }
        mockMvc.get("/api/avisos") {
            param("ubsId", "1")
            param("medicamentoIds", "2,-1")
        }.andExpect { status { isBadRequest() } }
        mockMvc.get("/api/avisos") {
            param("ubsId", "1")
            param("medicamentoIds", (1..51).joinToString(","))
        }.andExpect { status { isBadRequest() } }
        mockMvc.get("/api/avisos") {
            param("ubsId", "1")
            param("medicamentoIds", "abc")
        }.andExpect { status { isBadRequest() } }

        verifyNoInteractions(avisoService)
    }
}
