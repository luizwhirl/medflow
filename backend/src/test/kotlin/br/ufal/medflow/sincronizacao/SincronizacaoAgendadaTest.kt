package br.ufal.medflow.sincronizacao

import br.ufal.medflow.integracao.FonteEstoqueIndisponivelException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SincronizacaoAgendadaTest {

    private val servico = mock(SincronizacaoEstoqueService::class.java)

    private fun resultado(ibge: String) = ResultadoSincronizacao(
        ibge, 0, Descartes(), Contagem(), Contagem(), Contagem(), Contagem(),
    )

    private fun agendada(vararg municipios: String) =
        SincronizacaoAgendada(servico, SincronizacaoProperties(ativo = true, municipios = municipios.toList()))

    @Test
    fun `sincroniza cada municipio configurado`() {
        given(servico.sincronizar("2702405")).willReturn(resultado("2702405"))
        given(servico.sincronizar("2708501")).willReturn(resultado("2708501"))

        val resultados = agendada("2702405", "2708501").executar()

        assertEquals(listOf("2702405", "2708501"), resultados.map { it.codigoIbge })
    }

    @Test
    fun `falha em um municipio nao impede os outros`() {
        given(servico.sincronizar("2702405")).willThrow(FonteEstoqueIndisponivelException("fora do ar"))
        given(servico.sincronizar("2708501")).willReturn(resultado("2708501"))

        val resultados = agendada("2702405", "2708501").executar()

        assertEquals(listOf("2708501"), resultados.map { it.codigoIbge })
    }

    @Test
    fun `erro inesperado tambem nao derruba o job`() {
        given(servico.sincronizar("2702405")).willThrow(IllegalStateException("bug"))
        given(servico.sincronizar("2708501")).willReturn(resultado("2708501"))

        assertEquals(1, agendada("2702405", "2708501").executar().size)
    }

    @Test
    fun `sem municipios configurados nao faz nada`() {
        assertEquals(emptyList(), agendada().executar())
        verify(servico, never()).sincronizar("2702405")
    }

    @Test
    fun `so executa ao iniciar quando configurado`() {
        given(servico.sincronizar("2702405")).willReturn(resultado("2702405"))

        SincronizacaoAgendada(servico, SincronizacaoProperties(true, listOf("2702405"), executarAoIniciar = false)).aoIniciar()
        verify(servico, never()).sincronizar("2702405")

        SincronizacaoAgendada(servico, SincronizacaoProperties(true, listOf("2702405"), executarAoIniciar = true)).aoIniciar()
        verify(servico).sincronizar("2702405")
    }

    @Configuration
    class ConfigComServico {
        @Bean
        fun servico(): SincronizacaoEstoqueService = mock(SincronizacaoEstoqueService::class.java)
    }

    @Test
    fun `o job so existe com app sync ativo`() {
        val contexto = ApplicationContextRunner()
            .withUserConfiguration(SincronizacaoConfig::class.java, ConfigComServico::class.java, SincronizacaoAgendada::class.java)
            .withPropertyValues("app.sync.cron=0 0 3 * * *")

        contexto.run { assertNull(it.getBeanProvider(SincronizacaoAgendada::class.java).ifAvailable) }
        contexto.withPropertyValues("app.sync.ativo=true")
            .run { assertNotNull(it.getBeanProvider(SincronizacaoAgendada::class.java).ifAvailable) }
    }
}
