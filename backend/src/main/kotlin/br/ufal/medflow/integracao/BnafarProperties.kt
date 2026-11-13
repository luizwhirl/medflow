package br.ufal.medflow.integracao

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.bnafar")
data class BnafarProperties(
    val baseUrl: String = "https://apidadosabertos.saude.gov.br",
    /** A API aceita até 1000 registros por página. */
    val tamanhoPagina: Int = 1000,
    /** Trava de segurança contra paginação infinita. */
    val maxPaginas: Int = 500,
    val timeoutConexao: Duration = Duration.ofSeconds(5),
    val timeoutLeitura: Duration = Duration.ofSeconds(30),
)
