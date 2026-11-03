package br.ufal.medflow.medicamento

import br.ufal.medflow.estoque.ItemEstoqueRepository
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** Teste da camada web, sem banco (não precisa de Docker). */
@WebMvcTest(MedicamentoController::class)
class MedicamentoControllerTest(@Autowired private val mockMvc: MockMvc) {

    @MockitoBean
    private lateinit var medicamentoRepository: MedicamentoRepository

    @MockitoBean
    private lateinit var itemEstoqueRepository: ItemEstoqueRepository

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

    @Test
    fun `busca ignora espacos nas pontas do termo`() {
        given(medicamentoRepository.buscar("metformina"))
            .willReturn(listOf(metformina))

        mockMvc.get("/api/medicamentos") {
            param("busca", "  metformina ")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].nome") { value("Metformina") }
            jsonPath("$[0].formaFarmaceutica") { value("comprimido") }
        }

        verify(medicamentoRepository).buscar("metformina")
    }

    @Test
    fun `sem termo de busca lista todos os medicamentos`() {
        given(medicamentoRepository.findAllByOrderByNomeAsc())
            .willReturn(listOf(ibuprofeno, metformina))

        mockMvc.get("/api/medicamentos").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].nome") { value("Ibuprofeno") }
            jsonPath("$[1].nome") { value("Metformina") }
        }

        verify(medicamentoRepository).findAllByOrderByNomeAsc()
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
        given(medicamentoRepository.buscarPorCategoria("Diabetes"))
            .willReturn(listOf(metformina))

        mockMvc.get("/api/medicamentos") {
            param("categoria", "Diabetes")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].nome") { value("Metformina") }
            jsonPath("$[0].categoria") { value("Diabetes") }
        }

        verify(medicamentoRepository).buscarPorCategoria("Diabetes")
    }

    @Test
    fun `combina busca textual com categoria`() {
        given(
            medicamentoRepository.buscarPorCategoriaETermo(
                "metformina",
                "Diabetes",
            ),
        ).willReturn(listOf(metformina))

        mockMvc.get("/api/medicamentos") {
            param("busca", "metformina")
            param("categoria", "Diabetes")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].nome") { value("Metformina") }
            jsonPath("$[0].categoria") { value("Diabetes") }
        }

        verify(medicamentoRepository).buscarPorCategoriaETermo(
            "metformina",
            "Diabetes",
        )
    }

    @Test
    fun `categoria inexistente retorna lista vazia`() {
        given(medicamentoRepository.buscarPorCategoria("Inexistente"))
            .willReturn(emptyList())

        mockMvc.get("/api/medicamentos") {
            param("categoria", "Inexistente")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }

        verify(medicamentoRepository).buscarPorCategoria("Inexistente")
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
}
