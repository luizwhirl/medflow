package br.ufal.medflow.auth

import br.ufal.medflow.TestcontainersConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Servidor de verdade, porque o MockMvc não faz o encaminhamento de erros para `/error`. Quando o Spring trata
 * um erro com `sendError` (corpo inválido, parâmetro com tipo errado, método não permitido...), a resposta passa
 * pelo filtro de segurança de novo, e sem liberar esse caminho quem não tem token recebe 401 no lugar do código certo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class ErrosComServidorRealIntegrationTests(
    @Autowired private val usuarios: UsuarioRepository,
) {
    @LocalServerPort
    private var porta: Int = 0

    private val http = HttpClient.newHttpClient()

    private data class Resposta(val status: Int, val corpo: String)

    private fun chamar(
        metodo: String,
        caminho: String,
        corpo: String? = null,
        tipo: String = "application/json",
        token: String? = null,
    ): Resposta {
        val publicador = if (corpo == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(corpo)
        val pedido = HttpRequest.newBuilder(URI.create("http://localhost:$porta$caminho"))
            .method(metodo, publicador)
            .header("Content-Type", tipo)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        val resposta = http.send(pedido, HttpResponse.BodyHandlers.ofString())
        return Resposta(resposta.statusCode(), resposta.body())
    }

    @AfterEach
    fun limpar() = usuarios.deleteAll()

    private fun esperar(status: Int, resposta: Resposta) {
        assertEquals(status, resposta.status, "corpo: ${resposta.corpo}")
        assertFalse("Não autenticado" in resposta.corpo, "o erro não pode virar 401: ${resposta.corpo}")
    }

    @Test
    fun `corpo invalido no cadastro retorna 400 e nao 401`() {
        esperar(400, chamar("POST", "/api/auth/cadastro", """{"nome":"a","email":"x","senha":"12345678"}"""))
    }

    @Test
    fun `json malformado retorna 400 e nao 401`() {
        esperar(400, chamar("POST", "/api/auth/login", """{ isto nao e json"""))
    }

    @Test
    fun `tipo de conteudo nao suportado retorna 415 e nao 401`() {
        esperar(415, chamar("POST", "/api/auth/login", "texto", tipo = "text/plain"))
    }

    @Test
    fun `parametro com tipo errado retorna 400 e nao 401`() {
        esperar(400, chamar("GET", "/api/medicamentos/abc"))
        esperar(400, chamar("GET", "/api/ubs/abc/estoque"))
    }

    @Test
    fun `validacao de query param retorna 400 e nao 401`() {
        esperar(400, chamar("GET", "/api/ubs?lat=91&lng=0"))
        esperar(400, chamar("GET", "/api/ubs?lat=0&lng=0&raioKm=0"))
    }

    @Test
    fun `rota protegida sem token continua 401 no formato problem json`() {
        val resposta = chamar("GET", "/api/auth/eu")

        assertEquals(401, resposta.status)
        assertTrue("Não autenticado" in resposta.corpo, resposta.corpo)
    }

    @Test
    fun `com token, rota inexistente e metodo nao permitido retornam 404 e 405`() {
        chamar("POST", "/api/auth/cadastro", """{"nome":"Rita","email":"rita@teste.com","senha":"senha-valida-123"}""")
        val token = Regex("\"token\":\"([^\"]+)\"").find(
            chamar("POST", "/api/auth/login", """{"email":"rita@teste.com","senha":"senha-valida-123"}""").corpo,
        )!!.groupValues[1]

        esperar(404, chamar("GET", "/api/rota-que-nao-existe", token = token))
        esperar(405, chamar("POST", "/api/ubs", "{}", token = token))
    }

    @Test
    fun `o endereco de erro nao vira uma porta aberta para quem pede direto`() {
        // o /error só é liberado para o encaminhamento de um erro; pedido direto de quem não tem token continua barrado
        assertEquals(401, chamar("GET", "/error").status)
    }
}
