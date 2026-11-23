package br.ufal.medflow.perfil

import br.ufal.medflow.auth.CredenciaisInvalidasException
import br.ufal.medflow.auth.SecurityConfig
import br.ufal.medflow.common.RecursoNaoEncontradoException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put

/**
 * Testes da camada web do perfil do cidadão.
 *
 * Utiliza o SecurityConfig real e simula somente
 * o serviço de perfil e a decodificação dos JWTs.
 *
 * Não precisa de banco de dados nem Docker.
 */
@WebMvcTest(PerfilCidadaoController::class)
@Import(SecurityConfig::class)
class PerfilCidadaoControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @MockitoBean
    private lateinit var perfilService: PerfilCidadaoService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private val usuarioId = 7L

    private val perfilInicial = PerfilCidadaoResponse(
        id = usuarioId,
        nome = "Dona Rita",
        email = "rita@medflow.local",
        ubsReferencia = null,
        bairro = null,
        latitudeAproximada = null,
        longitudeAproximada = null,
        letraMaior = false,
        salvarBuscasRecentes = false,
        buscasRecentes = emptyList(),
    )

    private val perfilAtualizado = PerfilCidadaoResponse(
        id = usuarioId,
        nome = "Rita Maria",
        email = "rita@medflow.local",
        ubsReferencia = UbsReferenciaResponse(
            id = 3L,
            cnes = "9000103",
            nome = "UBS Clima Bom",
            bairro = "Clima Bom",
            endereco = "Rua do Posto, 300",
        ),
        bairro = "Clima Bom",
        latitudeAproximada = -9.56,
        longitudeAproximada = -35.74,
        letraMaior = true,
        salvarBuscasRecentes = true,
        buscasRecentes = listOf("Metformina", "Losartana"),
    )

    private val requestAtualizacao = AtualizarPerfilRequest(
        nome = "Rita Maria",
        ubsReferenciaId = 3L,
        bairro = "Clima Bom",
        latitudeAproximada = -9.56,
        longitudeAproximada = -35.74,
        letraMaior = true,
        salvarBuscasRecentes = true,
        buscasRecentes = listOf("Metformina", "Losartana"),
    )

    private val corpoAtualizacao = """
        {
          "nome": "Rita Maria",
          "ubsReferenciaId": 3,
          "bairro": "Clima Bom",
          "latitudeAproximada": -9.56,
          "longitudeAproximada": -35.74,
          "letraMaior": true,
          "salvarBuscasRecentes": true,
          "buscasRecentes": [
            "Metformina",
            "Losartana"
          ]
        }
    """.trimIndent()

    /**
     * Configura um token reconhecido pelo JwtDecoder simulado.
     *
     * A conversão da claim papel para ROLE_CIDADAO
     * ou ROLE_GESTOR é feita pelo SecurityConfig.
     */
    private fun autenticar(
        token: String,
        id: String = usuarioId.toString(),
        papel: String = "CIDADAO",
    ) {
        val jwt = Jwt.withTokenValue(token)
            .header("alg", "HS256")
            .subject(id)
            .claim("papel", papel)
            .build()

        given(jwtDecoder.decode(token)).willReturn(jwt)
    }

    @Test
    fun `cidadao autenticado consulta o proprio perfil`() {
        autenticar("token-cidadao")

        given(perfilService.consultar(usuarioId))
            .willReturn(perfilInicial)

        mockMvc.get("/api/me") {
            header("Authorization", "Bearer token-cidadao")
        }.andExpect {
            status { isOk() }

            jsonPath("$.id") { value(7) }
            jsonPath("$.nome") { value("Dona Rita") }
            jsonPath("$.email") {
                value("rita@medflow.local")
            }

            jsonPath("$.ubsReferencia") { value(null) }
            jsonPath("$.bairro") { value(null) }

            jsonPath("$.letraMaior") { value(false) }
            jsonPath("$.salvarBuscasRecentes") {
                value(false)
            }

            jsonPath("$.buscasRecentes.length()") {
                value(0)
            }
        }

        verify(perfilService).consultar(usuarioId)
    }

    @Test
    fun `cidadao autenticado atualiza o proprio perfil`() {
        autenticar("token-atualizacao")

        given(
            perfilService.atualizar(
                usuarioId,
                requestAtualizacao,
            ),
        ).willReturn(perfilAtualizado)

        mockMvc.put("/api/me") {
            header("Authorization", "Bearer token-atualizacao")
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao
        }.andExpect {
            status { isOk() }

            jsonPath("$.id") { value(7) }
            jsonPath("$.nome") { value("Rita Maria") }

            jsonPath("$.ubsReferencia.id") { value(3) }
            jsonPath("$.ubsReferencia.nome") {
                value("UBS Clima Bom")
            }

            jsonPath("$.bairro") { value("Clima Bom") }
            jsonPath("$.latitudeAproximada") {
                value(-9.56)
            }
            jsonPath("$.longitudeAproximada") {
                value(-35.74)
            }

            jsonPath("$.letraMaior") { value(true) }
            jsonPath("$.salvarBuscasRecentes") {
                value(true)
            }

            jsonPath("$.buscasRecentes.length()") {
                value(2)
            }
            jsonPath("$.buscasRecentes[0]") {
                value("Metformina")
            }
            jsonPath("$.buscasRecentes[1]") {
                value("Losartana")
            }
        }

        verify(perfilService).atualizar(
            usuarioId,
            requestAtualizacao,
        )
    }

    @Test
    fun `ubs de referencia inexistente retorna 404 padronizado`() {
        autenticar("token-ubs-inexistente")

        val request = requestAtualizacao.copy(
            ubsReferenciaId = 999L,
        )

        given(
            perfilService.atualizar(usuarioId, request),
        ).willThrow(
            RecursoNaoEncontradoException("UBS", 999L),
        )

        mockMvc.put("/api/me") {
            header(
                "Authorization",
                "Bearer token-ubs-inexistente",
            )
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao.replace(
                "\"ubsReferenciaId\": 3",
                "\"ubsReferenciaId\": 999",
            )
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.title") {
                value("Recurso não encontrado")
            }
            jsonPath("$.status") { value(404) }
        }

        verify(perfilService).atualizar(usuarioId, request)
    }

    @Test
    fun `nome vazio retorna 400 antes de chamar o servico`() {
        autenticar("token-nome-invalido")

        mockMvc.put("/api/me") {
            header("Authorization", "Bearer token-nome-invalido")
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao.replace(
                "\"nome\": \"Rita Maria\"",
                "\"nome\": \"   \"",
            )
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(perfilService)
    }

    @Test
    fun `latitude fora do intervalo retorna 400`() {
        autenticar("token-latitude-invalida")

        mockMvc.put("/api/me") {
            header(
                "Authorization",
                "Bearer token-latitude-invalida",
            )
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao.replace(
                "\"latitudeAproximada\": -9.56",
                "\"latitudeAproximada\": 91.0",
            )
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(perfilService)
    }

    @Test
    fun `mais de dez buscas recentes retorna 400`() {
        autenticar("token-buscas-invalidas")

        val onzeBuscas = (1..11).joinToString(",") {
            "\"Busca $it\""
        }

        mockMvc.put("/api/me") {
            header(
                "Authorization",
                "Bearer token-buscas-invalidas",
            )
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "nome": "Dona Rita",
                  "ubsReferenciaId": null,
                  "bairro": null,
                  "latitudeAproximada": null,
                  "longitudeAproximada": null,
                  "letraMaior": false,
                  "salvarBuscasRecentes": true,
                  "buscasRecentes": [$onzeBuscas]
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(perfilService)
    }

    @Test
    fun `requisicoes sem autenticacao retornam 401`() {
        mockMvc.get("/api/me").andExpect {
            status { isUnauthorized() }
        }

        mockMvc.put("/api/me") {
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao
        }.andExpect {
            status { isUnauthorized() }
        }

        verifyNoInteractions(perfilService)
    }

    @Test
    fun `gestor nao pode consultar nem atualizar perfil de cidadao`() {
        autenticar(
            token = "token-gestor",
            papel = "GESTOR",
        )

        mockMvc.get("/api/me") {
            header("Authorization", "Bearer token-gestor")
        }.andExpect {
            status { isForbidden() }
        }

        mockMvc.put("/api/me") {
            header("Authorization", "Bearer token-gestor")
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao
        }.andExpect {
            status { isForbidden() }
        }

        verifyNoInteractions(perfilService)
    }

    @Test
    fun `token com identificador nao numerico retorna 401 padronizado`() {
        autenticar(
            token = "token-sub-invalido",
            id = "usuario-invalido",
        )

        mockMvc.get("/api/me") {
            header(
                "Authorization",
                "Bearer token-sub-invalido",
            )
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.title") {
                value("Credenciais inválidas")
            }
        }

        verifyNoInteractions(perfilService)
    }

    @Test
    fun `usuario nao encontrado retorna 401 padronizado`() {
        autenticar("token-usuario-inexistente")

        given(
            perfilService.consultar(usuarioId),
        ).willThrow(
            CredenciaisInvalidasException(),
        )

        mockMvc.get("/api/me") {
            header(
                "Authorization",
                "Bearer token-usuario-inexistente",
            )
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.title") {
                value("Credenciais inválidas")
            }
        }

        verify(perfilService).consultar(usuarioId)
    }

    @Test
    fun `o id do usuario vem do jwt e nao do corpo`() {
        autenticar(
            token = "token-outro-cidadao",
            id = "42",
        )

        val outroPerfil = perfilInicial.copy(
            id = 42L,
            nome = "Outro Cidadão",
            email = "outro@medflow.local",
        )

        given(perfilService.consultar(42L))
            .willReturn(outroPerfil)

        mockMvc.get("/api/me") {
            header(
                "Authorization",
                "Bearer token-outro-cidadao",
            )
        }.andExpect {
            status { isOk() }

            jsonPath("$.id") { value(42) }
            jsonPath("$.nome") {
                value("Outro Cidadão")
            }
        }

        verify(perfilService).consultar(42L)
    }
}
