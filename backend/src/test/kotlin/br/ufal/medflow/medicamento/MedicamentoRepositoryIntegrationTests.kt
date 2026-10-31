package br.ufal.medflow.medicamento

import br.ufal.medflow.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class MedicamentoRepositoryIntegrationTests(
    @Autowired private val medicamentoRepository: MedicamentoRepository,
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
        )

        assertTrue(resultado.any { it.id == medicamentoDiabetes.id })
        assertTrue(resultado.none { it.id == medicamentoOutraCategoria.id })
        assertEquals(
            1,
            resultado.count { it.nome == "Medicamento Teste Filtro" },
        )
    }
}
