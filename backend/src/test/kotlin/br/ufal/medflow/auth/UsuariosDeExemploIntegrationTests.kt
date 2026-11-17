package br.ufal.medflow.auth

import br.ufal.medflow.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

/** Perfil "dev": sobe com as duas contas de exemplo (e o seed). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class UsuariosDeExemploIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val exemplos: UsuariosDeExemplo,
) {
    @Test
    fun `o perfil dev sobe com uma conta de cidadao e uma de gestor`() {
        assertEquals(PapelUsuario.CIDADAO, usuarios.findByEmail("rita@medflow.local")?.papel)
        assertEquals(PapelUsuario.GESTOR, usuarios.findByEmail("carlos@medflow.local")?.papel)
    }

    @Test
    fun `as contas de exemplo entram com a senha documentada`() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"carlos@medflow.local","senha":"${UsuariosDeExemplo.SENHA}"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.papel") { value("GESTOR") }
        }
    }

    @Test
    fun `rodar de novo nao duplica as contas`() {
        val antes = usuarios.count()

        exemplos.run(DefaultApplicationArguments())

        assertEquals(antes, usuarios.count())
    }
}
