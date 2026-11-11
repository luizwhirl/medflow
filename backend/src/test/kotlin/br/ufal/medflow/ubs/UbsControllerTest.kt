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

    @MockitoBean
    private lateinit var ubsProximidadeService: UbsProximidadeService

    private fun ubsProxima(distanciaKm: Double? = null) = UbsProximaResponse(
        id = 1, cnes = "9000101", nome = "UBS Teste", tipo = TipoUnidade.UBS, bairro = "Centro", endereco = "Rua A, 1",
        telefone = null, horario = null, latitude = -9.5, longitude = -35.7, ultimaSincronizacao = null,
        distanciaKm = distanciaKm,
    )

    @Test
    fun `lista com lat e lng delega ao servico e devolve a distancia`() {
        given(ubsProximidadeService.buscar(-9.5, -35.7, 3.0, 7L)).willReturn(listOf(ubsProxima(distanciaKm = 1.25)))

        mockMvc.get("/api/ubs") {
            param("lat", "-9.5")
            param("lng", "-35.7")
            param("raioKm", "3")
            param("medicamentoId", "7")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].nome") { value("UBS Teste") }
            jsonPath("$[0].distanciaKm") { value(1.25) }
            jsonPath("$[0].status") { doesNotExist() }
        }

        verify(ubsProximidadeService).buscar(-9.5, -35.7, 3.0, 7L)
    }

    @Test
    fun `lista sem parametros continua por nome e sem os campos novos`() {
        given(ubsRepository.findAllByOrderByNomeAsc()).willReturn(emptyList())

        mockMvc.get("/api/ubs").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }

        verifyNoInteractions(ubsProximidadeService)
    }

    @Test
    fun `lat sem lng, ou lng sem lat, retorna 400`() {
        mockMvc.get("/api/ubs") { param("lat", "-9.5") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("Parâmetro inválido") }
        }
        mockMvc.get("/api/ubs") { param("lng", "-35.7") }.andExpect { status { isBadRequest() } }

        verifyNoInteractions(ubsProximidadeService)
    }

    @Test
    fun `raioKm e medicamentoId sem coordenadas retornam 400`() {
        mockMvc.get("/api/ubs") { param("raioKm", "3") }.andExpect { status { isBadRequest() } }
        mockMvc.get("/api/ubs") { param("medicamentoId", "7") }.andExpect { status { isBadRequest() } }

        verifyNoInteractions(ubsProximidadeService)
    }

    @Test
    fun `coordenadas fora do intervalo e raio nao positivo retornam 400`() {
        listOf(
            mapOf("lat" to "91", "lng" to "0"),
            mapOf("lat" to "-91", "lng" to "0"),
            mapOf("lat" to "0", "lng" to "181"),
            mapOf("lat" to "0", "lng" to "-181"),
            mapOf("lat" to "0", "lng" to "0", "raioKm" to "0"),
            mapOf("lat" to "0", "lng" to "0", "raioKm" to "-5"),
            mapOf("lat" to "abc", "lng" to "0"),
        ).forEach { params ->
            mockMvc.get("/api/ubs") { params.forEach { (k, v) -> param(k, v) } }
                .andExpect { status { isBadRequest() } }
        }

        verifyNoInteractions(ubsProximidadeService)
    }

    @Test
    fun `coordenadas nos limites do intervalo sao aceitas`() {
        given(ubsProximidadeService.buscar(90.0, 180.0, null, null)).willReturn(emptyList())

        mockMvc.get("/api/ubs") {
            param("lat", "90")
            param("lng", "180")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `medicamento inexistente na busca por proximidade retorna 404`() {
        given(ubsProximidadeService.buscar(0.0, 0.0, null, 99L))
            .willThrow(RecursoNaoEncontradoException("Medicamento", 99L))

        mockMvc.get("/api/ubs") {
            param("lat", "0")
            param("lng", "0")
            param("medicamentoId", "99")
        }.andExpect { status { isNotFound() } }
    }

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
