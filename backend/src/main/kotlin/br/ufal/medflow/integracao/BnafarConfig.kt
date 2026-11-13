package br.ufal.medflow.integracao

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

@Configuration
@EnableConfigurationProperties(BnafarProperties::class)
class BnafarConfig {

    @Bean
    fun bnafarRestClient(props: BnafarProperties): RestClient {
        val http = HttpClient.newBuilder().connectTimeout(props.timeoutConexao).build()
        val fabrica = JdkClientHttpRequestFactory(http).apply { setReadTimeout(props.timeoutLeitura) }
        return RestClient.builder().baseUrl(props.baseUrl).requestFactory(fabrica).build()
    }
}
