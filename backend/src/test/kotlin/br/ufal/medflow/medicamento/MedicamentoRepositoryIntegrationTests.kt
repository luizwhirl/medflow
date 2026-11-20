package br.ufal.medflow.medicamento

import br.ufal.medflow.TestcontainersConfiguration
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Pageable
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class MedicamentoRepositoryIntegrationTests(
    @Autowired private val medicamentoRepository: MedicamentoRepository,
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    @Transactional
    fun `busca combinada retorna apenas medicamentos da categoria informada`() {
        val medicamentoDiabetes = medicamentoRepository.saveAndFlush(
            Medicamento(
                catmat = "TESTE-DIAB-001",
                nome = "Medicamento Teste Filtro",
                concentracao = "500 mg",
                formaFarmaceutica = "comprimido",
                categoria = "Diabetes",
            ),
        )

        val medicamentoOutraCategoria = medicamentoRepository.saveAndFlush(
            Medicamento(
                catmat = "TESTE-DOR-001",
                nome = "Medicamento Teste Filtro",
                concentracao = "200 mg",
                formaFarmaceutica = "comprimido",
                categoria = "Dor e febre",
            ),
        )

        val resultado = medicamentoRepository.buscarPorCategoriaETermo(
            "Medicamento Teste Filtro",
            "Diabetes",
            Pageable.unpaged(),
        )

        assertTrue(resultado.any { it.id == medicamentoDiabetes.id })
        assertTrue(resultado.none { it.id == medicamentoOutraCategoria.id })
        assertEquals(
            1,
            resultado.count { it.nome == "Medicamento Teste Filtro" },
        )
    }

    @Test
    fun `API combina busca parcial com categoria`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "met")
            param("categoria", "Diabetes")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens.length()") { value(1) }
            jsonPath("$.itens[0].nome") { value("Metformina") }
            jsonPath("$.itens[0].categoria") { value("Diabetes") }
        }
    }

    @Test
    fun `API filtra categoria ignorando maiusculas e minusculas`() {
        mockMvc.get("/api/medicamentos") {
            param("categoria", "diabetes")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens[*].categoria") {
                value(everyItem(equalTo("Diabetes")))
            }
            jsonPath("$.itens[*].nome") {
                value(hasItem("Metformina"))
            }
        }
    }
}
