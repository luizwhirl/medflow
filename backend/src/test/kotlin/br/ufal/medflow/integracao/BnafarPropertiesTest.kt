package br.ufal.medflow.integracao

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import java.time.Duration
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

            assertEquals(
                "https://apidadosabertos.saude.gov.br",
                props.baseUrl,
            )
            assertEquals(1000, props.tamanhoPagina)
            assertEquals(500, props.maxPaginas)
            assertEquals(Duration.ofSeconds(5), props.timeoutConexao)
            assertEquals(Duration.ofSeconds(30), props.timeoutLeitura)
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

    @Test
    fun `baseUrl nao pode ser vazio ou conter apenas espacos`() {
        for (url in listOf("", "   ")) {
            contexto.withPropertyValues(
                "app.bnafar.base-url=$url",
            ).run { context ->
                assertNotNull(context.startupFailure)
            }
        }
    }

    @Test
    fun `baseUrl nao vazio e aceito`() {
        val url = "https://exemplo.saude.gov.br"

        contexto.withPropertyValues(
            "app.bnafar.base-url=$url",
        ).run { context ->
            assertNull(context.startupFailure)

            val props = context.getBean(BnafarProperties::class.java)

            assertEquals(url, props.baseUrl)
        }
    }

    @Test
    fun `timeouts nulos ou negativos impedem inicializacao`() {
        for (propriedade in listOf(
            "timeout-conexao",
            "timeout-leitura",
        )) {
            for (valor in listOf("0s", "-1s")) {
                contexto.withPropertyValues(
                    "app.bnafar.$propriedade=$valor",
                ).run { context ->
                    assertNotNull(
                        context.startupFailure,
                        "$propriedade=$valor deveria impedir a inicialização",
                    )
                }
            }
        }
    }

    @Test
    fun `timeouts positivos sao aceitos`() {
        contexto.withPropertyValues(
            "app.bnafar.timeout-conexao=1ms",
            "app.bnafar.timeout-leitura=2s",
        ).run { context ->
            assertNull(context.startupFailure)

            val props = context.getBean(BnafarProperties::class.java)

            assertEquals(
                Duration.ofMillis(1),
                props.timeoutConexao,
            )
            assertEquals(
                Duration.ofSeconds(2),
                props.timeoutLeitura,
            )
        }
    }
}
