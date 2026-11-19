package br.ufal.medflow.listadesejo

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.auth.JwtProperties
import br.ufal.medflow.auth.JwtService
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import kotlin.test.assertNotNull

/** Testes da lista de desejos com JWT real e PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ItemListaDesejoIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val ubsRepository: UbsRepository,
    @Autowired private val medicamentoRepository: MedicamentoRepository,
    @Autowired private val jwtService: JwtService,
    @Autowired private val senhas: PasswordEncoder,
    @Autowired private val jwtEncoder: JwtEncoder,
    @Autowired private val jwtProperties: JwtProperties,
    @Autowired private val relogio: Clock,
) {

    private fun cidadao(email: String): Usuario =
        usuarios.saveAndFlush(
            Usuario(
                nome = "Cidadão Teste",
                email = email,
                senhaHash = requireNotNull(senhas.encode("senha-teste-123")),
                papel = PapelUsuario.CIDADAO,
            ),
        )

    private fun ubs(): Ubs =
        ubsRepository.saveAndFlush(
            Ubs(
                cnes = "9000101",
                nome = "UBS Teste",
                tipo = TipoUnidade.UBS,
                bairro = "Centro",
                endereco = "Rua A, 1",
                codigoIbgeMunicipio = "2704302",
                latitude = -9.5,
                longitude = -35.7,
            ),
        )

    private fun medicamento(): Medicamento =
        medicamentoRepository.saveAndFlush(
            Medicamento(
                catmat = "BR0267690",
                nome = "Metformina",
                concentracao = "850 mg",
                formaFarmaceutica = "Comprimido",
                categoria = "Diabetes",
            ),
        )

    private fun token(usuario: Usuario): String =
        jwtService.emitir(usuario).token

    /** Emite um JWT válido, mas com sub que não representa um ID numérico. */
    private fun tokenComSubjectInvalido(): String {
        val agora = relogio.instant()

        val claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.emissor)
            .issuedAt(agora)
            .expiresAt(agora.plus(jwtProperties.expiracao))
            .subject("usuario-invalido")
            .claim("email", "invalido@teste.com")
            .claim("papel", PapelUsuario.CIDADAO.name)
            .build()

        val cabecalho = JwsHeader.with(MacAlgorithm.HS256).build()

        return jwtEncoder.encode(
            JwtEncoderParameters.from(cabecalho, claims),
        ).tokenValue
    }

    private fun criar(
        token: String,
        ubsId: Long,
        medicamentoId: Long,
    ) = mockMvc.post("/api/lista-desejos") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"ubsId":$ubsId,"medicamentoId":$medicamentoId}"""
    }

    private fun extrairId(json: String): Long {
        val resultado = Regex("\"id\":(\\d+)").find(json)
        return assertNotNull(resultado).groupValues[1].toLong()
    }

    @Test
    fun `sem token as rotas exigem o identificador do aparelho`() {
        mockMvc.get("/api/lista-desejos").andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.title") { value("Parâmetro inválido") }
        }

        mockMvc.post("/api/lista-desejos") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ubsId":1,"medicamentoId":1}"""
        }.andExpect {
            status { isBadRequest() }
        }

        mockMvc.patch("/api/lista-desejos/1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"notificar":false}"""
        }.andExpect {
            status { isBadRequest() }
        }

        mockMvc.delete("/api/lista-desejos/1").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `jwt com subject nao numerico retorna 401`() {
        val jwt = tokenComSubjectInvalido()

        mockMvc.get("/api/lista-desejos") {
            header("Authorization", "Bearer $jwt")
        }.andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(401) }
            jsonPath("$.title") { value("Credenciais inválidas") }
        }
    }

    @Test
    fun `token de usuario excluido retorna 401 em todas as operacoes`() {
        val usuario = cidadao("excluido@teste.com")
        val jwt = token(usuario)

        // O JWT ainda é válido, mas a conta deixa de existir no banco.
        usuarios.delete(usuario)
        usuarios.flush()

        mockMvc.get("/api/lista-desejos") {
            header("Authorization", "Bearer $jwt")
        }.andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(401) }
            jsonPath("$.title") { value("Credenciais inválidas") }
        }

        mockMvc.post("/api/lista-desejos") {
            header("Authorization", "Bearer $jwt")
            contentType = MediaType.APPLICATION_JSON
            content = """{"ubsId":1,"medicamentoId":1}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.title") { value("Credenciais inválidas") }
        }

        mockMvc.patch("/api/lista-desejos/1") {
            header("Authorization", "Bearer $jwt")
            contentType = MediaType.APPLICATION_JSON
            content = """{"notificar":false}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.title") { value("Credenciais inválidas") }
        }

        mockMvc.delete("/api/lista-desejos/1") {
            header("Authorization", "Bearer $jwt")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.title") { value("Credenciais inválidas") }
        }
    }

    @Test
    fun `cidadao cria e lista seus medicamentos`() {
        val usuario = cidadao("rita@teste.com")
        val unidade = ubs()
        val remedio = medicamento()
        val jwt = token(usuario)

        criar(jwt, requireNotNull(unidade.id), requireNotNull(remedio.id)).andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.ubsId") { value(unidade.id) }
            jsonPath("$.ubsNome") { value("UBS Teste") }
            jsonPath("$.medicamentoId") { value(remedio.id) }
            jsonPath("$.medicamentoNome") { value("Metformina") }
            jsonPath("$.notificar") { value(true) }
            jsonPath("$.criadoEm") { exists() }
        }

        mockMvc.get("/api/lista-desejos") {
            header("Authorization", "Bearer $jwt")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].medicamentoNome") { value("Metformina") }
            jsonPath("$[0].ubsNome") { value("UBS Teste") }
        }
    }

    @Test
    fun `medicamento duplicado retorna 409`() {
        val usuario = cidadao("rita@teste.com")
        val unidade = ubs()
        val remedio = medicamento()
        val jwt = token(usuario)

        criar(jwt, requireNotNull(unidade.id), requireNotNull(remedio.id))
            .andExpect {
                status { isCreated() }
            }

        criar(jwt, requireNotNull(unidade.id), requireNotNull(remedio.id))
            .andExpect {
                status { isConflict() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.status") { value(409) }
                jsonPath("$.title") { value("Item já cadastrado") }
            }
    }

    @Test
    fun `cidadao atualiza notificacao e remove item`() {
        val usuario = cidadao("rita@teste.com")
        val unidade = ubs()
        val remedio = medicamento()
        val jwt = token(usuario)

        val resposta = criar(
            jwt,
            requireNotNull(unidade.id),
            requireNotNull(remedio.id),
        ).andExpect {
            status { isCreated() }
        }.andReturn().response.contentAsString

        val itemId = extrairId(resposta)

        mockMvc.patch("/api/lista-desejos/$itemId") {
            header("Authorization", "Bearer $jwt")
            contentType = MediaType.APPLICATION_JSON
            content = """{"notificar":false}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(itemId) }
            jsonPath("$.notificar") { value(false) }
        }

        mockMvc.delete("/api/lista-desejos/$itemId") {
            header("Authorization", "Bearer $jwt")
        }.andExpect {
            status { isNoContent() }
        }

        mockMvc.get("/api/lista-desejos") {
            header("Authorization", "Bearer $jwt")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `cidadao nao acessa nem modifica itens de outro usuario`() {
        val rita = cidadao("rita@teste.com")
        val carlos = cidadao("carlos@teste.com")
        val unidade = ubs()
        val remedio = medicamento()

        val tokenRita = token(rita)
        val tokenCarlos = token(carlos)

        val resposta = criar(
            tokenRita,
            requireNotNull(unidade.id),
            requireNotNull(remedio.id),
        ).andExpect {
            status { isCreated() }
        }.andReturn().response.contentAsString

        val itemId = extrairId(resposta)

        mockMvc.get("/api/lista-desejos") {
            header("Authorization", "Bearer $tokenCarlos")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }

        mockMvc.patch("/api/lista-desejos/$itemId") {
            header("Authorization", "Bearer $tokenCarlos")
            contentType = MediaType.APPLICATION_JSON
            content = """{"notificar":false}"""
        }.andExpect {
            status { isNotFound() }
        }

        mockMvc.delete("/api/lista-desejos/$itemId") {
            header("Authorization", "Bearer $tokenCarlos")
        }.andExpect {
            status { isNotFound() }
        }

        mockMvc.get("/api/lista-desejos") {
            header("Authorization", "Bearer $tokenRita")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].notificar") { value(true) }
        }
    }

    @Test
    fun `ubs ou medicamento inexistente retorna 404`() {
        val usuario = cidadao("rita@teste.com")
        val unidade = ubs()
        val remedio = medicamento()
        val jwt = token(usuario)

        criar(jwt, 999999L, requireNotNull(remedio.id)).andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }

        criar(jwt, requireNotNull(unidade.id), 999999L).andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
    }

    @Test
    fun `notificacao nula e identificadores invalidos retornam 400`() {
        val usuario = cidadao("rita@teste.com")
        val jwt = token(usuario)

        mockMvc.post("/api/lista-desejos") {
            header("Authorization", "Bearer $jwt")
            contentType = MediaType.APPLICATION_JSON
            content = """{"ubsId":0,"medicamentoId":-1}"""
        }.andExpect {
            status { isBadRequest() }
        }

        mockMvc.patch("/api/lista-desejos/1") {
            header("Authorization", "Bearer $jwt")
            contentType = MediaType.APPLICATION_JSON
            content = """{"notificar":null}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
