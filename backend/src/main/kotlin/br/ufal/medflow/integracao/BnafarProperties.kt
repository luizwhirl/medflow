package br.ufal.medflow.integracao

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.hibernate.validator.constraints.time.DurationMin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties("app.bnafar")
data class BnafarProperties(
    @field:NotBlank
    val baseUrl: String = "https://apidadosabertos.saude.gov.br",

    /** A API aceita até 1000 registros por página. */
    @field:Min(1)
    @field:Max(1000)
    val tamanhoPagina: Int = 1000,

    /** Trava de segurança contra paginação infinita. */
    @field:Min(1)
    val maxPaginas: Int = 500,

    @field:DurationMin(inclusive = false)
    val timeoutConexao: Duration = Duration.ofSeconds(5),

    @field:DurationMin(inclusive = false)
    val timeoutLeitura: Duration = Duration.ofSeconds(30),
)
