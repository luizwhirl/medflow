package br.ufal.medflow.auth

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JwtTest {

    private val props = JwtProperties(secret = "segredo-so-para-testes-com-mais-de-32-caracteres", expiracao = Duration.ofHours(8))
    private val config = SecurityConfig(props)
    private val usuario = Usuario("Seu Carlos", "carlos@teste.com", "hash", PapelUsuario.GESTOR, id = 42)

    @Test
    fun `token leva emissor, sub, papel, e-mail e validade de 8 horas`() {
        val agora = Instant.now()
        val servico = JwtService(config.jwtEncoder(), props, Clock.fixed(agora, ZoneOffset.UTC))

        val emitido = servico.emitir(usuario)
        val jwt = config.jwtDecoder().decode(emitido.token)

        assertEquals("medflow-api", jwt.getClaimAsString("iss"))
        assertEquals("42", jwt.subject)
        assertEquals("GESTOR", jwt.getClaimAsString("papel"))
        assertEquals("carlos@teste.com", jwt.getClaimAsString("email"))
        assertEquals(agora.epochSecond, jwt.issuedAt!!.epochSecond)
        assertEquals(agora.plus(Duration.ofHours(8)).epochSecond, jwt.expiresAt!!.epochSecond)
        assertEquals(8 * 3600L, emitido.expiraEmSegundos)
    }

    @Test
    fun `token nao carrega a senha nem o hash`() {
        val token = JwtService(config.jwtEncoder(), props, Clock.systemUTC()).emitir(usuario).token
        val corpo = String(java.util.Base64.getUrlDecoder().decode(token.split(".")[1]))

        assertTrue("hash" !in corpo, corpo)
        assertTrue("senha" !in corpo.lowercase(), corpo)
    }

    @Test
    fun `token emitido no passado ja nasce expirado`() {
        val ontem = Clock.fixed(Instant.now().minus(Duration.ofDays(1)), ZoneOffset.UTC)
        val token = JwtService(config.jwtEncoder(), props, ontem).emitir(usuario).token

        assertFailsWith<org.springframework.security.oauth2.jwt.JwtException> { config.jwtDecoder().decode(token) }
    }

    @Test
    fun `segredo curto ou vazio impede a aplicacao de configurar o JWT`() {
        assertFailsWith<IllegalArgumentException> { JwtProperties(secret = "") }
        assertFailsWith<IllegalArgumentException> { JwtProperties(secret = "curto-demais") }
        assertFailsWith<IllegalArgumentException> { JwtProperties(secret = "x".repeat(31)) }
        JwtProperties(secret = "x".repeat(32)) // o mínimo vale
    }

    @Test
    fun `expiracao zero ou negativa e rejeitada`() {
        assertFailsWith<IllegalArgumentException> { JwtProperties(secret = "x".repeat(32), expiracao = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { JwtProperties(secret = "x".repeat(32), expiracao = Duration.ofHours(-1)) }
    }

    /** Só as propriedades, para o teste falhar pelo segredo e não por falta de outro bean. */
    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(JwtProperties::class)
    class SoAsPropriedades

    @Test
    fun `sem JWT_SECRET a aplicacao nao sobe`() {
        val contexto = ApplicationContextRunner().withUserConfiguration(SoAsPropriedades::class.java)

        contexto.run { assertNotNull(it.startupFailure, "deveria falhar sem segredo") }
        contexto.withPropertyValues("app.jwt.secret=curto").run { assertNotNull(it.startupFailure) }
        contexto.withPropertyValues("app.jwt.secret=" + "x".repeat(32)).run {
            assertEquals(null, it.startupFailure, "com segredo válido deve subir")
            assertEquals(Duration.ofHours(8), it.getBean(JwtProperties::class.java).expiracao)
        }
        contexto.withPropertyValues("app.jwt.secret=" + "x".repeat(32), "app.jwt.expiracao=30m").run {
            assertEquals(Duration.ofMinutes(30), it.getBean(JwtProperties::class.java).expiracao)
        }
    }
}
