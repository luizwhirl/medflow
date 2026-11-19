package br.ufal.medflow.ubs

import br.ufal.medflow.TestcontainersConfiguration
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Migration V9 e seed V1005 (perfil "dev") com o relógio parado numa sexta, 10:00 em Maceió.
 * No seed, só a USF Riacho Doce (9000107, Seg a Qui) está fechada às sextas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class, HorarioFuncionamentoIntegrationTests.RelogioDeSexta::class)
@Testcontainers(disabledWithoutDocker = true)
class HorarioFuncionamentoIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val ubsRepository: UbsRepository,
) {

    @TestConfiguration(proxyBeanMethods = false)
    class RelogioDeSexta {
        @Bean
        @Primary
        fun relogioDeSexta(): Clock = Clock.fixed(Instant.parse("2026-11-20T13:00:00Z"), ZoneOffset.UTC)
    }

    private fun idPorCnes(cnes: String) = ubsRepository.findByCnesIn(listOf(cnes)).single().id!!

    @Test
    fun `lista traz horarios, abertaAgora e fechaAs vindos do seed`() {
        mockMvc.get("/api/ubs").andExpect {
            status { isOk() }
            jsonPath("$[?(@.cnes == '9000101')].abertaAgora") { value(hasItem(true)) }
            jsonPath("$[?(@.cnes == '9000101')].fechaAs") { value(hasItem("17:00")) }
            jsonPath("$[?(@.cnes == '9000104')].fechaAs") { value(hasItem("19:00")) }
            jsonPath("$[?(@.cnes == '9000107')].abertaAgora") { value(hasItem(false)) }
        }
    }

    @Test
    fun `abertas=true deixa de fora a unidade fechada`() {
        mockMvc.get("/api/ubs") { param("abertas", "true") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(6) }
            jsonPath("$[*].cnes") { value(not(hasItem("9000107"))) }
        }
    }

    @Test
    fun `abertas=true tambem filtra a busca por proximidade`() {
        mockMvc.get("/api/ubs") {
            param("lat", "-9.55")
            param("lng", "-35.72")
            param("raioKm", "50")
            param("abertas", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(6) }
            jsonPath("$[*].cnes") { value(not(hasItem("9000107"))) }
            jsonPath("$[0].distanciaKm") { exists() }
        }
    }

    @Test
    fun `detalhe traz os turnos por dia da semana`() {
        mockMvc.get("/api/ubs/{id}", idPorCnes("9000107")).andExpect {
            status { isOk() }
            jsonPath("$.horarios[*].diaSemana") { value(containsInAnyOrder("SEGUNDA", "TERCA", "QUARTA", "QUINTA")) }
            jsonPath("$.horarios[0].abre") { value("08:00") }
            jsonPath("$.horarios[0].fecha") { value("14:00") }
            jsonPath("$.abertaAgora") { value(false) }
        }
    }
}
