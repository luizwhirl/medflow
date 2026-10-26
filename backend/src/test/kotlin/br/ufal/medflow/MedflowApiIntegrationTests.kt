package br.ufal.medflow

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Sobe a aplicação inteira contra um PostgreSQL real (Testcontainers), com as migrations
 * e os dados de exemplo do perfil "dev". É ignorado automaticamente se o Docker não estiver rodando.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class MedflowApiIntegrationTests(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `lista as UBS cadastradas`() {
        mockMvc.get("/api/ubs").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(7) }
        }
    }

    @Test
    fun `busca medicamento por nome sem diferenciar maiusculas`() {
        mockMvc.get("/api/medicamentos") { param("busca", "metFORmina") }.andExpect {
            status { isOk() }
            jsonPath("$[0].catmat") { value("BR0267690") }
        }
    }

    @Test
    fun `disponibilidade lista primeiro as unidades com estoque`() {
        mockMvc.get("/api/medicamentos/1/disponibilidade").andExpect {
            status { isOk() }
            jsonPath("$[0].status") { value("DISPONIVEL") }
        }
    }

    @Test
    fun `UBS inexistente retorna 404`() {
        mockMvc.get("/api/ubs/999").andExpect { status { isNotFound() } }
    }
}
