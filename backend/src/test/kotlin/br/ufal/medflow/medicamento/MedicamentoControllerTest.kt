package br.ufal.medflow.medicamento

import br.ufal.medflow.auth.SecurityConfig
import br.ufal.medflow.common.Paginacao
import br.ufal.medflow.common.ParametroInvalidoException
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.ResumoDisponibilidadeResponse
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** Teste da camada web, sem banco (não precisa de Docker). */
@WebMvcTest(MedicamentoController::class)
@Import(SecurityConfig::class) // a segurança real: leitura pública, como em produção
class MedicamentoControllerTest(@Autowired private val mockMvc: MockMvc) {

    private companion object {
        /** Página padrão da API: a primeira, 20 itens, em ordem de nome. */
        val PADRAO = Paginacao.de(0, Paginacao.TAMANHO_PADRAO, "nome")
        val SEM_ORDEM = PageRequest.of(0, Paginacao.TAMANHO_PADRAO)
    }

    @MockitoBean
    private lateinit var medicamentoRepository: MedicamentoRepository

    @MockitoBean
    private lateinit var itemEstoqueRepository: ItemEstoqueRepository

    @MockitoBean
    private lateinit var medicamentoDisponibilidadeService: MedicamentoDisponibilidadeService

    private val metformina = Medicamento(
        catmat = "BR0267690",
        nome = "Metformina",
        concentracao = "850 mg",
        formaFarmaceutica = "comprimido",
        categoria = "Diabetes",
        id = 1,
    )

    private val ibuprofeno = Medicamento(
        catmat = "BR0267671",
        nome = "Ibuprofeno",
        concentracao = "600 mg",
        formaFarmaceutica = "comprimido",
        categoria = "Dor e febre",
        id = 2,
    )

    private fun pagina(vararg itens: Medicamento) = PageImpl(itens.toList(), PADRAO, itens.size.toLong())

    @Test
    fun `busca ignora espacos nas pontas do termo`() {
        given(medicamentoRepository.buscarPorRelevancia("metformina", "", SEM_ORDEM))
            .willReturn(pagina(metformina))

        mockMvc.get("/api/medicamentos") {
            param("busca", "  metformina ")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens[0].nome") { value("Metformina") }
            jsonPath("$.itens[0].formaFarmaceutica") { value("comprimido") }
        }

        verify(medicamentoRepository).buscarPorRelevancia("metformina", "", SEM_ORDEM)
    }

    @Test
    fun `sem termo de busca lista todos os medicamentos`() {
        given(medicamentoRepository.findAll(PADRAO))
            .willReturn(pagina(ibuprofeno, metformina))

        mockMvc.get("/api/medicamentos").andExpect {
            status { isOk() }
            jsonPath("$.itens.length()") { value(2) }
            jsonPath("$.itens[0].nome") { value("Ibuprofeno") }
            jsonPath("$.itens[1].nome") { value("Metformina") }
        }

        verify(medicamentoRepository).findAll(PADRAO)
    }

    @Test
    fun `medicamento inexistente retorna 404 em problem+json`() {
        mockMvc.get("/api/medicamentos/42").andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
    }

    @Test
    fun `filtra medicamentos por categoria`() {
        given(medicamentoRepository.buscarPorCategoria("Diabetes", SEM_ORDEM))
            .willReturn(pagina(metformina))

        mockMvc.get("/api/medicamentos") {
            param("categoria", "Diabetes")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens.length()") { value(1) }
            jsonPath("$.itens[0].nome") { value("Metformina") }
            jsonPath("$.itens[0].categoria") { value("Diabetes") }
        }

        verify(medicamentoRepository).buscarPorCategoria("Diabetes", SEM_ORDEM)
    }

    @Test
    fun `combina busca textual com categoria`() {
        given(
            medicamentoRepository.buscarPorRelevancia(
                "metformina",
                "Diabetes",
                SEM_ORDEM,
            ),
        ).willReturn(pagina(metformina))

        mockMvc.get("/api/medicamentos") {
            param("busca", "metformina")
            param("categoria", "Diabetes")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens.length()") { value(1) }
            jsonPath("$.itens[0].nome") { value("Metformina") }
            jsonPath("$.itens[0].categoria") { value("Diabetes") }
        }

        verify(medicamentoRepository).buscarPorRelevancia(
            "metformina",
            "Diabetes",
            SEM_ORDEM,
        )
    }

    @Test
    fun `categoria inexistente retorna lista vazia`() {
        given(medicamentoRepository.buscarPorCategoria("Inexistente", SEM_ORDEM))
            .willReturn(pagina())

        mockMvc.get("/api/medicamentos") {
            param("categoria", "Inexistente")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens.length()") { value(0) }
        }

        verify(medicamentoRepository).buscarPorCategoria("Inexistente", SEM_ORDEM)
    }

    @Test
    fun `lista categorias distintas`() {
        given(medicamentoRepository.listarCategorias())
            .willReturn(listOf("Diabetes", "Hipertensão"))

        mockMvc.get("/api/medicamentos/categorias").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0]") { value("Diabetes") }
            jsonPath("$[1]") { value("Hipertensão") }
        }

        verify(medicamentoRepository).listarCategorias()
    }

    @Test
    fun `resumo retorna unidades com estoque disponivel`() {
        given(medicamentoDisponibilidadeService.obterResumo(1L, null))
            .willReturn(
                ResumoDisponibilidadeResponse(
                    totalUnidades = 7L,
                    unidadesDisponiveis = 3L,
                ),
            )

        mockMvc.get("/api/medicamentos/1/disponibilidade/resumo").andExpect {
            status { isOk() }
            jsonPath("$.totalUnidades") { value(7) }
            jsonPath("$.unidadesDisponiveis") { value(3) }
        }

        verify(medicamentoDisponibilidadeService).obterResumo(1L, null)
    }

    @Test
    fun `resumo aceita filtro por municipio`() {
        given(medicamentoDisponibilidadeService.obterResumo(1L, "2704302"))
            .willReturn(
                ResumoDisponibilidadeResponse(
                    totalUnidades = 7L,
                    unidadesDisponiveis = 3L,
                ),
            )

        mockMvc.get("/api/medicamentos/1/disponibilidade/resumo") {
            param("ibge", "2704302")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalUnidades") { value(7) }
            jsonPath("$.unidadesDisponiveis") { value(3) }
        }

        verify(medicamentoDisponibilidadeService).obterResumo(1L, "2704302")
    }

    @Test
    fun `resumo retorna 400 para codigo IBGE invalido`() {
        given(medicamentoDisponibilidadeService.obterResumo(1L, "27043"))
            .willThrow(ParametroInvalidoException("ibge"))

        mockMvc.get("/api/medicamentos/1/disponibilidade/resumo") {
            param("ibge", "27043")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("Parâmetro inválido") }
            jsonPath("$.status") { value(400) }
            jsonPath("$.detail") { value("Parâmetro 'ibge' inválido") }
        }

        verify(medicamentoDisponibilidadeService).obterResumo(1L, "27043")
    }

    @Test
    fun `resumo de medicamento inexistente retorna 404`() {
        given(medicamentoDisponibilidadeService.obterResumo(42L, null))
            .willThrow(RecursoNaoEncontradoException("Medicamento", 42L))

        mockMvc.get("/api/medicamentos/42/disponibilidade/resumo").andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }

        verify(medicamentoDisponibilidadeService).obterResumo(42L, null)
    }

    @Test
    fun `aceita alternativas de reconhecimento na mesma requisicao`() {
        given(
            medicamentoRepository.buscarPorRelevancia(
                "losartan\nmetformina",
                "",
                SEM_ORDEM,
            ),
        ).willReturn(pagina(metformina))

        mockMvc.get("/api/medicamentos") {
            param("busca", "losartan", "metformina")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens[0].nome") { value("Metformina") }
        }

        verify(medicamentoRepository).buscarPorRelevancia(
            "losartan\nmetformina",
            "",
            SEM_ORDEM,
        )
    }

    @Test
    fun `rejeita mais de tres alternativas`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "a", "b", "c", "d")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("Parâmetro inválido") }
        }
    }

    @Test
    fun `rejeita alternativa excessivamente longa`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "a".repeat(201))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("Parâmetro inválido") }
        }
    }

    @Test
    fun `preserva virgulas dentro de uma unica transcricao`() {
        val termo = "remédio, de pressão, losartana, 50 mg"

        given(
            medicamentoRepository.buscarPorRelevancia(
                termo,
                "",
                SEM_ORDEM,
            ),
        ).willReturn(pagina())

        mockMvc.get("/api/medicamentos") {
            param("busca", termo)
        }.andExpect {
            status { isOk() }
        }

        verify(medicamentoRepository).buscarPorRelevancia(
            termo,
            "",
            SEM_ORDEM,
        )
    }

}
