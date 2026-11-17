package br.ufal.medflow.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.jwt")
data class JwtProperties(
    /** Segredo HS256, de pelo menos 32 caracteres. Em produção vem de `JWT_SECRET`; no perfil dev há um padrão. */
    val secret: String = "",
    val expiracao: Duration = Duration.ofHours(8),
    val emissor: String = "medflow-api",
) {
    init {
        require(secret.toByteArray().size >= 32) {
            "O segredo do JWT precisa ter pelo menos 32 caracteres: defina a variável de ambiente JWT_SECRET"
        }
        require(!expiracao.isZero && !expiracao.isNegative) { "app.jwt.expiracao precisa ser positiva" }
    }
}
