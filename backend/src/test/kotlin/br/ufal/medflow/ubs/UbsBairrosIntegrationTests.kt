package br.ufal.medflow.ubs

import br.ufal.medflow.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers

/** Bairros da boas-vindas, consultados no PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class UbsBairrosIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val ubsRepository: UbsRepository,
) {

    private fun ubs(cnes: String, bairro: String) =
        ubsRepository.saveAndFlush(
            Ubs(
                cnes = cnes,
                nome = "UBS $cnes",
                tipo = TipoUnidade.UBS,
                bairro = bairro,
                endereco = "Rua A, 1",
                codigoIbgeMunicipio = "2704302",
                latitude = -9.6,
                longitude = -35.7,
            ),
        )

    @Test
    fun `lista os bairros sem repeticao e em ordem alfabetica, sem login`() {
        ubs("7990001", "Tabuleiro do Martins")
        ubs("7990002", "Benedito Bentes")
        ubs("7990003", "Tabuleiro do Martins")

        mockMvc.get("/api/ubs/bairros").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0]") { value("Benedito Bentes") }
            jsonPath("$[1]") { value("Tabuleiro do Martins") }
        }
    }
}
