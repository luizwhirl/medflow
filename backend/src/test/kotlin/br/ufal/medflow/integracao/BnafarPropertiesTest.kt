package br.ufal.medflow.integracao

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Testes da validação das propriedades de configuração da BNAFAR. */
class BnafarPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BnafarProperties::class)
    class ConfiguracaoTeste

    private val contexto = ApplicationContextRunner()
        .withUserConfiguration(ConfiguracaoTeste::class.java)

    @Test
    fun `configuracao padrao e aceita`() {
        contexto.run { context ->
            assertNull(context.startupFailure)

            val props = context.getBean(BnafarProperties::class.java)

            assertEquals(1000, props.tamanhoPagina)
            assertEquals(500, props.maxPaginas)
        }
    }

    @Test
    fun `tamanhos de pagina validos sao aceitos`() {
        for (tamanho in listOf(1, 2, 1000)) {
            contexto.withPropertyValues(
                "app.bnafar.tamanho-pagina=$tamanho",
            ).run { context ->
                assertNull(context.startupFailure)

                val props = context.getBean(BnafarProperties::class.java)

                assertEquals(tamanho, props.tamanhoPagina)
            }
        }
    }

    @Test
    fun `tamanhos de pagina invalidos impedem inicializacao`() {
        for (tamanho in listOf(-1, 0, 1001)) {
            contexto.withPropertyValues(
                "app.bnafar.tamanho-pagina=$tamanho",
            ).run { context ->
                assertNotNull(context.startupFailure)
            }
        }
    }

    @Test
    fun `limite de paginas deve ser positivo`() {
        for (limite in listOf(-1, 0)) {
            contexto.withPropertyValues(
                "app.bnafar.max-paginas=$limite",
            ).run { context ->
                assertNotNull(context.startupFailure)
            }
        }

        contexto.withPropertyValues(
            "app.bnafar.max-paginas=1",
        ).run { context ->
            assertNull(context.startupFailure)

            val props = context.getBean(BnafarProperties::class.java)

            assertEquals(1, props.maxPaginas)
        }
    }
}
