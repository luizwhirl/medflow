package br.ufal.medflow.ubs

import br.ufal.medflow.TestcontainersConfiguration
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
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
import org.springframework.transaction.annotation.Transactional
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
@ActiveProfiles("dev", "demo")
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
    fun `fechaEm traz data, hora e fuso de Maceio`() {
        mockMvc.get("/api/ubs/{id}", idPorCnes("9000101")).andExpect {
            status { isOk() }
            jsonPath("$.fechaAs") { value("17:00") }
            jsonPath("$.fechaEm") { value("2026-11-20T17:00:00-03:00") }
        }
    }

    @Test
    fun `abertas=true deixa de fora a unidade fechada`() {
        mockMvc.get("/api/ubs") { param("abertas", "true") }.andExpect {
            status { isOk() }
            jsonPath("$[*].cnes") { value(hasItems(*ABERTAS_NA_SEXTA)) }
            jsonPath("$[*].cnes") { value(not(hasItem("9000107"))) }
        }
    }

    @Test
    @Transactional
    fun `UBS sem horario cadastrado fica sem turnos, com abertaAgora nulo e fora do filtro`() {
        val semHorario = ubsRepository.save(
            Ubs(
                cnes = "9999901",
                nome = "UBS Sem Horário",
                tipo = TipoUnidade.UBS,
                bairro = "Centro",
                endereco = "Rua B, 2",
                codigoIbgeMunicipio = "2704302",
                latitude = -9.66,
                longitude = -35.73,
            ),
        )

        mockMvc.get("/api/ubs/{id}", semHorario.id).andExpect {
            status { isOk() }
            jsonPath("$.horarios") { isEmpty() }
            jsonPath("$.abertaAgora") { value(nullValue()) }
            jsonPath("$.fechaAs") { value(nullValue()) }
            jsonPath("$.fechaEm") { value(nullValue()) }
        }
        mockMvc.get("/api/ubs") { param("abertas", "true") }.andExpect {
            status { isOk() }
            jsonPath("$[*].cnes") { value(not(hasItem("9999901"))) }
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
            jsonPath("$[*].cnes") { value(hasItems(*ABERTAS_NA_SEXTA)) }
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

    private companion object {
        /** Unidades do seed abertas na sexta às 10:00 (todas menos a 9000107, que atende de segunda a quinta). */
        val ABERTAS_NA_SEXTA = arrayOf("9000101", "9000102", "9000103", "9000104", "9000105", "9000106")
    }
}
