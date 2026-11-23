package br.ufal.medflow.ubs

import br.ufal.medflow.auth.SecurityConfig
import br.ufal.medflow.common.PaginaResponse
import br.ufal.medflow.common.Paginacao
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ResumoEstoqueUbsResponse
import br.ufal.medflow.estoque.StatusEstoque
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalTime
import java.util.Optional

/** Testes da API de UBS, sem banco de dados. */
@WebMvcTest(UbsController::class)
@Import(SecurityConfig::class) // a segurança real: leitura pública, como em produção
class UbsControllerTest(@Autowired private val mockMvc: MockMvc) {

    private companion object {
        /** Página padrão do estoque: a primeira, 20 itens, em ordem de nome do medicamento. */
        val ESTOQUE_PADRAO = Paginacao.de(0, Paginacao.TAMANHO_PADRAO, "medicamento.nome")
    }

    @MockitoBean
    private lateinit var ubsRepository: UbsRepository

    @MockitoBean
    private lateinit var ubsEstoqueService: UbsEstoqueService

    @MockitoBean
    private lateinit var ubsProximidadeService: UbsProximidadeService

    @MockitoBean
    private lateinit var horarioFuncionamentoService: HorarioFuncionamentoService

    private fun funcionamento(aberta: Boolean) = FuncionamentoUbs(
        horarios = listOf(HorarioResponse(DiaSemana.SEGUNDA, LocalTime.of(7, 0), LocalTime.of(17, 0))),
        abertaAgora = aberta,
        fechaAs = if (aberta) LocalTime.of(17, 0) else null,
    )

    private fun ubsProxima(distanciaKm: Double? = null, id: Long = 1) = UbsProximaResponse(
        id = id, cnes = "9000101", nome = "UBS Teste", tipo = TipoUnidade.UBS, bairro = "Centro", endereco = "Rua A, 1",
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
            jsonPath("$.detail") { value("Informe lat e lng juntos") }
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
    fun `erro de bean validation segue RFC 9457`() {
        mockMvc.get("/api/ubs") {
            param("lat", "91")
            param("lng", "0")
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(400) }
            jsonPath("$.title") { value("Bad Request") }
        }

        verifyNoInteractions(ubsProximidadeService)
    }

    @Test
    fun `erro de conversao de status segue RFC 9457`() {
        mockMvc.get("/api/ubs/1/estoque") {
            param("status", "XYZ")
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(400) }
            jsonPath("$.title") { value("Bad Request") }
        }

        verifyNoInteractions(ubsEstoqueService)
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
    fun `busca UBS existente por id`() {
        val ubs = Ubs(
            cnes = "9000101",
            nome = "UBS Teste",
            tipo = TipoUnidade.UBS,
            bairro = "Centro",
            endereco = "Rua A, 1",
            codigoIbgeMunicipio = "2704302",
            latitude = -9.5,
            longitude = -35.7,
            id = 1L,
        )

        given(ubsRepository.findById(1L)).willReturn(Optional.of(ubs))

        mockMvc.get("/api/ubs/1").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(1) }
            jsonPath("$.cnes") { value("9000101") }
            jsonPath("$.nome") { value("UBS Teste") }
            jsonPath("$.tipo") { value("UBS") }
            jsonPath("$.bairro") { value("Centro") }
            jsonPath("$.endereco") { value("Rua A, 1") }
            jsonPath("$.latitude") { value(-9.5) }
            jsonPath("$.longitude") { value(-35.7) }
        }

        verify(ubsRepository).findById(1L)
    }

    @Test
    fun `cada UBS traz os horarios e abertas=true deixa so as abertas`() {
        given(ubsProximidadeService.buscar(-9.5, -35.7, null, null))
            .willReturn(listOf(ubsProxima(1.0, id = 1), ubsProxima(2.0, id = 2), ubsProxima(3.0, id = 3)))
        given(horarioFuncionamentoService.buscar(listOf(1L, 2L, 3L)))
            .willReturn(mapOf(1L to funcionamento(aberta = true), 2L to funcionamento(aberta = false)))

        mockMvc.get("/api/ubs") {
            param("lat", "-9.5")
            param("lng", "-35.7")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].abertaAgora") { value(true) }
            jsonPath("$[0].fechaAs") { value("17:00") }
            jsonPath("$[0].horarios[0].diaSemana") { value("SEGUNDA") }
            jsonPath("$[0].horarios[0].abre") { value("07:00") }
            jsonPath("$[0].horarios[0].fecha") { value("17:00") }
            jsonPath("$[1].abertaAgora") { value(false) }
            jsonPath("$[2].horarios.length()") { value(0) }
        }

        mockMvc.get("/api/ubs") {
            param("lat", "-9.5")
            param("lng", "-35.7")
            param("abertas", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(1) }
        }
    }

    @Test
    fun `detalhe da UBS traz o funcionamento`() {
        val ubs = Ubs(
            cnes = "9000101", nome = "UBS Teste", tipo = TipoUnidade.UBS, bairro = "Centro", endereco = "Rua A, 1",
            codigoIbgeMunicipio = "2704302", latitude = -9.5, longitude = -35.7, id = 1L,
        )
        given(ubsRepository.findById(1L)).willReturn(Optional.of(ubs))
        given(horarioFuncionamentoService.buscar(listOf(1L))).willReturn(mapOf(1L to funcionamento(aberta = true)))

        mockMvc.get("/api/ubs/1").andExpect {
            status { isOk() }
            jsonPath("$.abertaAgora") { value(true) }
            jsonPath("$.fechaAs") { value("17:00") }
            jsonPath("$.horarios.length()") { value(1) }
        }
    }

    @Test
    fun `busca UBS inexistente retorna 404`() {
        given(ubsRepository.findById(999L)).willReturn(Optional.empty())

        mockMvc.get("/api/ubs/999").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(404) }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }

        verify(ubsRepository).findById(999L)
    }

    @Test
    fun `estoque retorna o formato paginado`() {
        given(ubsEstoqueService.listarItens(1L, null, ESTOQUE_PADRAO))
            .willReturn(PaginaResponse(emptyList(), 0, 20, 0, 0))

        mockMvc.get("/api/ubs/1/estoque").andExpect {
            status { isOk() }
            jsonPath("$.itens") { isArray() }
            jsonPath("$.itens.length()") { value(0) }
            jsonPath("$.totalItens") { value(0) }
        }

        verify(ubsEstoqueService).listarItens(1L, null, ESTOQUE_PADRAO)
    }

    @Test
    fun `estoque aceita filtro por status`() {
        given(ubsEstoqueService.listarItens(1L, StatusEstoque.DISPONIVEL, ESTOQUE_PADRAO))
            .willReturn(PaginaResponse(emptyList(), 0, 20, 0, 0))

        mockMvc.get("/api/ubs/1/estoque") {
            param("status", "DISPONIVEL")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens") { isArray() }
        }

        verify(ubsEstoqueService).listarItens(
            1L,
            StatusEstoque.DISPONIVEL,
            ESTOQUE_PADRAO,
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
        given(ubsEstoqueService.listarItens(999L, null, ESTOQUE_PADRAO))
            .willThrow(RecursoNaoEncontradoException("UBS", 999L))

        mockMvc.get("/api/ubs/999/estoque").andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
    }

    @Test
    fun `resumo de estoque de UBS inexistente retorna 404`() {
        given(ubsEstoqueService.obterResumo(999L, null))
            .willThrow(RecursoNaoEncontradoException("UBS", 999L))

        mockMvc.get("/api/ubs/999/estoque/resumo").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(404) }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }

        verify(ubsEstoqueService).obterResumo(999L, null)
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
