package br.ufal.medflow.listadesejo

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.auth.JwtService
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers

/** Verifica que apenas cidadãos podem utilizar a lista de desejos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ListaDesejosPermissoesIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val senhas: PasswordEncoder,
    @Autowired private val jwtService: JwtService,
) {

    @Test
    fun `gestor nao pode acessar lista de desejos`() {
        val gestor = usuarios.saveAndFlush(
            Usuario(
                nome = "Gestor Teste",
                email = "gestor-lista@teste.com",
                senhaHash = requireNotNull(senhas.encode("senha-teste-123")),
                papel = PapelUsuario.GESTOR,
            ),
        )

        val token = jwtService.emitir(gestor).token

        mockMvc.get("/api/lista-desejos") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isForbidden() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(403) }
            jsonPath("$.title") { value("Acesso negado") }
        }

        mockMvc.post("/api/lista-desejos") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"ubsId":1,"medicamentoId":1}"""
        }.andExpect {
            status { isForbidden() }
        }

        mockMvc.patch("/api/lista-desejos/1") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"notificar":false}"""
        }.andExpect {
            status { isForbidden() }
        }

        mockMvc.delete("/api/lista-desejos/1") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isForbidden() }
        }
    }
}
