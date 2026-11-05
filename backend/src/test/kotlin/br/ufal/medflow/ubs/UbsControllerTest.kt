package br.ufal.medflow.ubs

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ResumoEstoqueUbsResponse
import br.ufal.medflow.estoque.StatusEstoque
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** Testes da API de UBS, sem banco de dados. */
@WebMvcTest(UbsController::class)
class UbsControllerTest(@Autowired private val mockMvc: MockMvc) {

    @MockitoBean
    private lateinit var ubsRepository: UbsRepository

    @MockitoBean
    private lateinit var ubsEstoqueService: UbsEstoqueService

    @Test
    fun `estoque mantem retorno em lista`() {
        given(ubsEstoqueService.listarItens(1L, null))
            .willReturn(emptyList())

        mockMvc.get("/api/ubs/1/estoque").andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
            jsonPath("$.length()") { value(0) }
        }

        verify(ubsEstoqueService).listarItens(1L, null)
    }

    @Test
    fun `estoque aceita filtro por status`() {
        given(ubsEstoqueService.listarItens(1L, StatusEstoque.DISPONIVEL))
            .willReturn(emptyList())

        mockMvc.get("/api/ubs/1/estoque") {
            param("status", "DISPONIVEL")
        }.andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
        }

        verify(ubsEstoqueService).listarItens(
            1L,
            StatusEstoque.DISPONIVEL,
        )
    }

    @Test
    fun `resumo do estoque retorna contagens por status`() {
        given(ubsEstoqueService.obterResumo(1L, null))
            .willReturn(
                ResumoEstoqueUbsResponse(
                    disponivel = 2,
                    reservado = 1,
                    emFalta = 3,
                    itens = emptyList(),
                ),
            )

        mockMvc.get("/api/ubs/1/estoque/resumo").andExpect {
            status { isOk() }
            jsonPath("$.disponivel") { value(2) }
            jsonPath("$.reservado") { value(1) }
            jsonPath("$.emFalta") { value(3) }
            jsonPath("$.itens.length()") { value(0) }
        }

        verify(ubsEstoqueService).obterResumo(1L, null)
    }

    @Test
    fun `resumo aceita filtro por status sem alterar contagens`() {
        given(ubsEstoqueService.obterResumo(1L, StatusEstoque.DISPONIVEL))
            .willReturn(
                ResumoEstoqueUbsResponse(
                    disponivel = 2,
                    reservado = 1,
                    emFalta = 3,
                    itens = emptyList(),
                ),
            )

        mockMvc.get("/api/ubs/1/estoque/resumo") {
            param("status", "DISPONIVEL")
        }.andExpect {
            status { isOk() }
            jsonPath("$.disponivel") { value(2) }
            jsonPath("$.reservado") { value(1) }
            jsonPath("$.emFalta") { value(3) }
        }

        verify(ubsEstoqueService).obterResumo(
            1L,
            StatusEstoque.DISPONIVEL,
        )
    }

    @Test
    fun `estoque de UBS inexistente retorna 404`() {
        given(ubsEstoqueService.listarItens(999L, null))
            .willThrow(RecursoNaoEncontradoException("UBS", 999L))

        mockMvc.get("/api/ubs/999/estoque").andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
    }

    @Test
    fun `status invalido retorna 400 nas duas rotas`() {
        mockMvc.get("/api/ubs/1/estoque") {
            param("status", "XYZ")
        }.andExpect {
            status { isBadRequest() }
        }

        mockMvc.get("/api/ubs/1/estoque/resumo") {
            param("status", "XYZ")
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(ubsEstoqueService)
    }
}
