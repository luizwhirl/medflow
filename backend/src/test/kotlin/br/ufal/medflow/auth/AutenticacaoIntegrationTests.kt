package br.ufal.medflow.auth

import br.ufal.medflow.TestcontainersConfiguration
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.options
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Só existe nos testes: a rota de gestão de verdade vem com o painel (PDS-21). */
@RestController
@RequestMapping("/api/gestao")
class GestaoDeTesteController {
    @GetMapping("/teste")
    fun teste() = "ok"
}

/** Banco vazio (perfil "test"). Cada teste desfaz o que gravou. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, GestaoDeTesteController::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AutenticacaoIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val senhas: PasswordEncoder,
    @Autowired private val jwtService: JwtService,
    @Autowired private val jwtEncoder: JwtEncoder,
    @Autowired private val props: JwtProperties,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val senha = "senha-valida-123"

    private fun conta(email: String, papel: PapelUsuario, nome: String = "Fulano") =
        usuarios.saveAndFlush(Usuario(nome, email, requireNotNull(senhas.encode(senha)), papel))

    private fun tokenDe(usuario: Usuario) = jwtService.emitir(usuario).token

    private fun get(rota: String, token: String? = null) = mockMvc.get(rota) {
        token?.let { header("Authorization", "Bearer $it") }
    }

    private fun postJson(rota: String, json: String) = mockMvc.post(rota) {
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    private fun extrairToken(json: String) = Regex("\"token\":\"([^\"]+)\"").find(json)!!.groupValues[1]

    // --- os três critérios da issue: 401 sem token, 403 com papel errado, 200 com token válido

    @Test
    fun `rota de gestao sem token retorna 401`() {
        get("/api/gestao/teste").andExpect {
            status { isUnauthorized() }
            header { exists("WWW-Authenticate") }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.title") { value("Não autenticado") }
            jsonPath("$.status") { value(401) }
        }
    }

    @Test
    fun `rota de gestao com cidadao retorna 403`() {
        val cidadao = conta("rita@teste.com", PapelUsuario.CIDADAO)

        get("/api/gestao/teste", tokenDe(cidadao)).andExpect {
            status { isForbidden() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.title") { value("Acesso negado") }
        }
    }

    @Test
    fun `rota de gestao com gestor retorna 200`() {
        val gestor = conta("carlos@teste.com", PapelUsuario.GESTOR)

        get("/api/gestao/teste", tokenDe(gestor)).andExpect {
            status { isOk() }
            content { string("ok") }
        }
    }

    @Test
    fun `rota autenticada qualquer exige login, ate a que nao existe`() {
        get("/api/auth/eu").andExpect { status { isUnauthorized() } }
        get("/api/uma-rota-que-nao-existe").andExpect { status { isUnauthorized() } }
    }

    // --- o que é público

    @Test
    fun `leitura de UBS e medicamentos, saude e documentacao sao publicas`() {
        get("/api/ubs").andExpect { status { isOk() } }
        get("/api/medicamentos").andExpect { status { isOk() } }
        get("/actuator/health").andExpect { status { isOk() } }
        get("/v3/api-docs").andExpect { status { isOk() } }
    }

    // --- login

    @Test
    fun `login devolve o token, que funciona em rota autenticada`() {
        conta("rita@teste.com", PapelUsuario.CIDADAO, nome = "Dona Rita")

        val resposta = postJson("/api/auth/login", """{"email":"rita@teste.com","senha":"$senha"}""").andExpect {
            status { isOk() }
            jsonPath("$.tipo") { value("Bearer") }
            jsonPath("$.papel") { value("CIDADAO") }
            jsonPath("$.expiraEmSegundos") { value(props.expiracao.seconds) }
        }.andReturn().response.contentAsString

        get("/api/auth/eu", extrairToken(resposta)).andExpect {
            status { isOk() }
            jsonPath("$.nome") { value("Dona Rita") }
            jsonPath("$.email") { value("rita@teste.com") }
            jsonPath("$.papel") { value("CIDADAO") }
            jsonPath("$.senhaHash") { doesNotExist() }
        }
    }

    @Test
    fun `login ignora maiusculas e espacos no e-mail`() {
        conta("rita@teste.com", PapelUsuario.CIDADAO)

        postJson("/api/auth/login", """{"email":"  RITA@Teste.com ","senha":"$senha"}""").andExpect { status { isOk() } }
    }

    @Test
    fun `senha errada e e-mail desconhecido dao exatamente a mesma resposta`() {
        conta("rita@teste.com", PapelUsuario.CIDADAO)

        val senhaErrada = postJson("/api/auth/login", """{"email":"rita@teste.com","senha":"errada-errada"}""")
            .andExpect { status { isUnauthorized() } }.andReturn().response.contentAsString
        val emailDesconhecido = postJson("/api/auth/login", """{"email":"ninguem@teste.com","senha":"$senha"}""")
            .andExpect { status { isUnauthorized() } }.andReturn().response.contentAsString

        assertEquals(senhaErrada, emailDesconhecido, "a resposta não pode revelar se o e-mail tem conta")
        assertTrue("Credenciais inválidas" in senhaErrada)
    }

    @Test
    fun `login com campos vazios retorna 400 e com senha enorme retorna 401`() {
        postJson("/api/auth/login", """{"email":"","senha":""}""").andExpect { status { isBadRequest() } }
        postJson("/api/auth/login", """{"email":"rita@teste.com"}""").andExpect { status { isBadRequest() } }

        conta("rita@teste.com", PapelUsuario.CIDADAO)
        postJson("/api/auth/login", """{"email":"rita@teste.com","senha":"${"a".repeat(500)}"}""")
            .andExpect { status { isUnauthorized() } }
    }

    // --- tokens que não podem valer

    private fun claims(usuario: Usuario, emissor: String = props.emissor, sub: String = usuario.id.toString()) =
        JwtClaimsSet.builder().issuer(emissor).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
            .subject(sub).claim("email", usuario.email).claim("papel", usuario.papel.name).build()

    private fun assinar(encoder: JwtEncoder, claims: JwtClaimsSet) =
        encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue

    @Test
    fun `token adulterado, de outro segredo, sem assinatura, expirado ou de outro emissor retorna 401`() {
        val gestor = conta("carlos@teste.com", PapelUsuario.GESTOR)
        val valido = tokenDe(gestor)
        get("/api/gestao/teste", valido).andExpect { status { isOk() } } // confirma que o original passa

        val partes = valido.split(".")
        // o primeiro caractere vale 6 bits inteiros; o último tem 2 bits sem uso e trocá-lo às vezes não muda a assinatura
        val assinaturaTrocada = (if (partes[2].first() == 'A') "B" else "A") + partes[2].drop(1)
        val outroSegredo = NimbusJwtEncoder(ImmutableSecret(SecretKeySpec("outro-segredo-com-mais-de-32-caracteres".toByteArray(), "HmacSHA256")))
        val expirado = JwtService(jwtEncoder, props, Clock.fixed(Instant.now().minus(Duration.ofDays(1)), ZoneOffset.UTC)).emitir(gestor).token

        mapOf(
            "assinatura trocada" to "${partes[0]}.${partes[1]}.$assinaturaTrocada",
            "outro segredo" to assinar(outroSegredo, claims(gestor)),
            "sem assinatura (alg none)" to "eyJhbGciOiJub25lIn0.${partes[1]}.",
            "expirado" to expirado,
            "outro emissor" to assinar(jwtEncoder, claims(gestor, emissor = "outro-sistema")),
            "lixo" to "isto-nao-e-um-jwt",
        ).forEach { (motivo, token) ->
            get("/api/gestao/teste", token).andExpect {
                status { isUnauthorized() }
                jsonPath("$.title") { value("Não autenticado") }
            }.also { assertNotEquals(valido, token, motivo) }
        }
    }

    @Test
    fun `corpo do token adulterado para virar gestor nao passa`() {
        val cidadao = conta("rita@teste.com", PapelUsuario.CIDADAO)
        val partes = tokenDe(cidadao).split(".")
        val corpoGestor = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(String(java.util.Base64.getUrlDecoder().decode(partes[1])).replace("CIDADAO", "GESTOR").toByteArray())

        get("/api/gestao/teste", "${partes[0]}.$corpoGestor.${partes[2]}").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `token de conta que nao existe mais, ou com sub invalido, retorna 401`() {
        val rita = conta("rita@teste.com", PapelUsuario.CIDADAO)
        val token = tokenDe(rita)
        get("/api/auth/eu", token).andExpect { status { isOk() } }

        usuarios.delete(rita)
        usuarios.flush()
        get("/api/auth/eu", token).andExpect { status { isUnauthorized() } }

        val outra = conta("outra@teste.com", PapelUsuario.CIDADAO)
        get("/api/auth/eu", assinar(jwtEncoder, claims(outra, sub = "abc"))).andExpect { status { isUnauthorized() } }
    }

    // --- cadastro

    @Test
    fun `cadastro cria uma conta de cidadao e nunca devolve a senha`() {
        postJson("/api/auth/cadastro", """{"nome":"  Dona Rita ","email":"Rita@Teste.COM","senha":"$senha"}""").andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.nome") { value("Dona Rita") }
            jsonPath("$.email") { value("rita@teste.com") }
            jsonPath("$.papel") { value("CIDADAO") }
            jsonPath("$.senha") { doesNotExist() }
            jsonPath("$.senhaHash") { doesNotExist() }
        }

        postJson("/api/auth/login", """{"email":"rita@teste.com","senha":"$senha"}""").andExpect { status { isOk() } }
    }

    @Test
    fun `a senha e guardada como hash BCrypt`() {
        postJson("/api/auth/cadastro", """{"nome":"Rita","email":"rita@teste.com","senha":"$senha"}""").andExpect { status { isCreated() } }

        val hash = jdbc.queryForObject("select senha_hash from usuario where email = 'rita@teste.com'", String::class.java)!!

        assertNotEquals(senha, hash)
        assertTrue(hash.startsWith("\$2"), "deveria ser BCrypt: $hash")
        assertTrue(senhas.matches(senha, hash))
    }

    @Test
    fun `quem se cadastra nao consegue escolher o papel`() {
        val resposta = postJson(
            "/api/auth/cadastro",
            """{"nome":"Esperto","email":"esperto@teste.com","senha":"$senha","papel":"GESTOR"}""",
        ).andExpect {
            status { isCreated() }
            jsonPath("$.papel") { value("CIDADAO") }
        }.andReturn().response.contentAsString

        assertTrue("GESTOR" !in resposta)
        val token = extrairToken(
            postJson("/api/auth/login", """{"email":"esperto@teste.com","senha":"$senha"}""").andReturn().response.contentAsString,
        )
        get("/api/gestao/teste", token).andExpect { status { isForbidden() } }
        assertEquals("CIDADAO", jdbc.queryForObject("select papel from usuario where email = 'esperto@teste.com'", String::class.java))
    }

    @Test
    fun `e-mail repetido retorna 409, mesmo com outra caixa`() {
        postJson("/api/auth/cadastro", """{"nome":"Rita","email":"rita@teste.com","senha":"$senha"}""").andExpect { status { isCreated() } }

        postJson("/api/auth/cadastro", """{"nome":"Outra","email":"RITA@teste.com","senha":"$senha"}""").andExpect {
            status { isConflict() }
            jsonPath("$.title") { value("E-mail já cadastrado") }
        }
        assertEquals(1L, jdbc.queryForObject("select count(*) from usuario", Long::class.java))
    }

    @Test
    fun `cadastro invalido retorna 400`() {
        listOf(
            """{"nome":"Rita","email":"rita@teste.com","senha":"curta"}""", // menos de 8 caracteres
            """{"nome":"Rita","email":"nao-e-email","senha":"$senha"}""",
            """{"nome":"","email":"rita@teste.com","senha":"$senha"}""",
            """{"nome":"Rita","email":"rita@teste.com","senha":"${"a".repeat(73)}"}""", // passa do limite do BCrypt
            """{"nome":"Rita","email":"rita@teste.com","senha":"${"é".repeat(40)}"}""", // 80 bytes
            """{"nome":"Rita"}""",
        ).forEach { json -> postJson("/api/auth/cadastro", json).andExpect { status { isBadRequest() } } }

        assertEquals(0L, jdbc.queryForObject("select count(*) from usuario", Long::class.java))
    }

    // --- CORS com o cabeçalho Authorization

    @Test
    fun `preflight do front libera o cabecalho Authorization sem exigir login`() {
        mockMvc.options("/api/auth/eu") {
            header("Origin", "http://localhost:4200")
            header("Access-Control-Request-Method", "GET")
            header("Access-Control-Request-Headers", "authorization,content-type")
        }.andExpect {
            status { isOk() }
            header { string("Access-Control-Allow-Origin", "http://localhost:4200") }
            header { string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsStringIgnoringCase("authorization")) }
        }
    }

    @Test
    fun `origem nao permitida e barrada no preflight`() {
        mockMvc.options("/api/auth/eu") {
            header("Origin", "http://site-estranho.com")
            header("Access-Control-Request-Method", "GET")
        }.andExpect { status { isForbidden() } }
    }
}
