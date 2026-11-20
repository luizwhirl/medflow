package br.ufal.medflow.perfil

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.auth.JwtService
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes de integração da API de perfil do cidadão.
 *
 * Utiliza JWT real e PostgreSQL via Testcontainers.
 * Cada teste é executado em uma transação revertida ao final.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PerfilCidadaoIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val unidades: UbsRepository,
    @Autowired private val buscas: BuscaRecenteUsuarioRepository,
    @Autowired private val jwtService: JwtService,
    @Autowired private val em: EntityManager,
) {

    private fun criarUsuario(
        email: String,
        papel: PapelUsuario = PapelUsuario.CIDADAO,
    ): Usuario =
        usuarios.saveAndFlush(
            Usuario(
                nome = "Cidadão Teste",
                email = email,
                senhaHash = "hash-teste",
                papel = papel,
            ),
        )

    private fun criarUbs(cnes: String): Ubs =
        unidades.saveAndFlush(
            Ubs(
                cnes = cnes,
                nome = "UBS Centro",
                tipo = TipoUnidade.UBS,
                bairro = "Centro",
                endereco = "Rua Principal, 100",
                codigoIbgeMunicipio = "2704302",
                latitude = -9.665,
                longitude = -35.735,
            ),
        )

    private fun token(usuario: Usuario): String =
        jwtService.emitir(usuario).token

    /**
     * Monta o JSON de atualização do perfil.
     *
     * Os valores usados aqui são controlados pelos próprios testes.
     */
    private fun corpoAtualizacao(
        nome: String = "Rita Maria",
        ubsId: Long? = null,
        bairro: String? = "Clima Bom",
        latitude: Double? = -9.56167,
        longitude: Double? = -35.74167,
        letraMaior: Boolean = true,
        salvarBuscas: Boolean = true,
        buscasJson: String = """["Metformina", "Losartana"]""",
    ): String =
        """
        {
          "nome": "$nome",
          "ubsReferenciaId": ${ubsId?.toString() ?: "null"},
          "bairro": ${bairro?.let { "\"$it\"" } ?: "null"},
          "latitudeAproximada": ${latitude?.toString() ?: "null"},
          "longitudeAproximada": ${longitude?.toString() ?: "null"},
          "letraMaior": $letraMaior,
          "salvarBuscasRecentes": $salvarBuscas,
          "buscasRecentes": $buscasJson
        }
        """.trimIndent()

    @Test
    fun `consulta perfil inicial do cidadao autenticado`() {
        val cidadao = criarUsuario("perfil-inicial@teste.com")

        mockMvc.get("/api/me") {
            header("Authorization", "Bearer ${token(cidadao)}")
        }.andExpect {
            status { isOk() }

            jsonPath("$.id") {
                value(requireNotNull(cidadao.id).toInt())
            }
            jsonPath("$.nome") { value("Cidadão Teste") }
            jsonPath("$.email") {
                value("perfil-inicial@teste.com")
            }

            jsonPath("$.ubsReferencia") { value(null) }
            jsonPath("$.bairro") { value(null) }
            jsonPath("$.latitudeAproximada") { value(null) }
            jsonPath("$.longitudeAproximada") { value(null) }

            jsonPath("$.letraMaior") { value(false) }
            jsonPath("$.salvarBuscasRecentes") { value(false) }
            jsonPath("$.buscasRecentes.length()") { value(0) }
        }
    }

    @Test
    fun `atualiza e persiste o perfil com ubs de referencia`() {
        val cidadao = criarUsuario("perfil-atualizacao@teste.com")
        val ubs = criarUbs("9000901")

        val authorization = "Bearer ${token(cidadao)}"

        mockMvc.put("/api/me") {
            header("Authorization", authorization)
            contentType = MediaType.APPLICATION_JSON

            content = corpoAtualizacao(
                nome = "  Rita   Maria  ",
                ubsId = requireNotNull(ubs.id),
                bairro = "  Clima   Bom  ",
                buscasJson = """
                    [
                      " Metformina ",
                      "METFORMINA",
                      "Losartana"
                    ]
                """.trimIndent(),
            )
        }.andExpect {
            status { isOk() }

            jsonPath("$.nome") { value("Rita Maria") }

            jsonPath("$.ubsReferencia.id") {
                value(requireNotNull(ubs.id).toInt())
            }
            jsonPath("$.ubsReferencia.nome") {
                value("UBS Centro")
            }
            jsonPath("$.ubsReferencia.cnes") {
                value("9000901")
            }

            jsonPath("$.bairro") { value("Clima Bom") }

            jsonPath("$.latitudeAproximada") {
                value(-9.562)
            }
            jsonPath("$.longitudeAproximada") {
                value(-35.742)
            }

            jsonPath("$.letraMaior") { value(true) }
            jsonPath("$.salvarBuscasRecentes") { value(true) }

            jsonPath("$.buscasRecentes.length()") { value(2) }
            jsonPath("$.buscasRecentes[0]") {
                value("Metformina")
            }
            jsonPath("$.buscasRecentes[1]") {
                value("Losartana")
            }
        }

        em.flush()
        em.clear()

        mockMvc.get("/api/me") {
            header("Authorization", authorization)
        }.andExpect {
            status { isOk() }

            jsonPath("$.nome") { value("Rita Maria") }
            jsonPath("$.ubsReferencia.id") {
                value(requireNotNull(ubs.id).toInt())
            }
            jsonPath("$.bairro") { value("Clima Bom") }
            jsonPath("$.letraMaior") { value(true) }

            jsonPath("$.buscasRecentes.length()") { value(2) }
            jsonPath("$.buscasRecentes[0]") {
                value("Metformina")
            }
        }

        val usuarioPersistido = usuarios.findById(
            requireNotNull(cidadao.id),
        ).orElseThrow()

        assertEquals("Rita Maria", usuarioPersistido.nome)
        assertEquals(ubs.id, usuarioPersistido.ubsReferencia?.id)

        val historico = buscas.listarPorUsuario(
            requireNotNull(cidadao.id),
        )

        assertEquals(2, historico.size)
        assertEquals("Metformina", historico[0].termo)
        assertEquals("Losartana", historico[1].termo)
    }

    @Test
    fun `ubs de referencia inexistente retorna 404 sem alterar perfil`() {
        val cidadao = criarUsuario("perfil-ubs-404@teste.com")
        val authorization = "Bearer ${token(cidadao)}"

        mockMvc.put("/api/me") {
            header("Authorization", authorization)
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao(
                ubsId = 999999L,
            )
        }.andExpect {
            status { isNotFound() }
        }

        em.clear()

        val usuario = usuarios.findById(
            requireNotNull(cidadao.id),
        ).orElseThrow()

        assertEquals("Cidadão Teste", usuario.nome)
        assertEquals(null, usuario.ubsReferencia)
        assertEquals(false, usuario.letraMaior)
        assertEquals(false, usuario.salvarBuscasRecentes)
    }

    @Test
    fun `ubs com id negativo retorna 400`() {
        val cidadao = criarUsuario("perfil-ubs-invalida@teste.com")

        mockMvc.put("/api/me") {
            header("Authorization", "Bearer ${token(cidadao)}")
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao(
                ubsId = -5L,
            )
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `localizacao incompleta retorna 400 sem atualizar perfil`() {
        val cidadao = criarUsuario("perfil-localizacao@teste.com")

        mockMvc.put("/api/me") {
            header("Authorization", "Bearer ${token(cidadao)}")
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao(
                latitude = -9.56,
                longitude = null,
            )
        }.andExpect {
            status { isBadRequest() }
        }

        em.clear()

        val usuario = usuarios.findById(
            requireNotNull(cidadao.id),
        ).orElseThrow()

        assertEquals("Cidadão Teste", usuario.nome)
        assertEquals(null, usuario.latitudeAproximada)
        assertEquals(null, usuario.longitudeAproximada)
    }

    @Test
    fun `desativar historico remove as buscas e permite retirar a ubs`() {
        val cidadao = criarUsuario("perfil-limpeza@teste.com")
        val ubs = criarUbs("9000902")
        val authorization = "Bearer ${token(cidadao)}"

        mockMvc.put("/api/me") {
            header("Authorization", authorization)
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao(
                ubsId = requireNotNull(ubs.id),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.buscasRecentes.length()") { value(2) }
        }

        em.flush()
        em.clear()

        mockMvc.put("/api/me") {
            header("Authorization", authorization)
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao(
                nome = "Rita Maria",
                ubsId = null,
                bairro = null,
                latitude = null,
                longitude = null,
                letraMaior = false,
                salvarBuscas = false,
                buscasJson = """["Busca que não deve ser salva"]""",
            )
        }.andExpect {
            status { isOk() }

            jsonPath("$.ubsReferencia") { value(null) }
            jsonPath("$.bairro") { value(null) }
            jsonPath("$.latitudeAproximada") { value(null) }
            jsonPath("$.longitudeAproximada") { value(null) }

            jsonPath("$.letraMaior") { value(false) }
            jsonPath("$.salvarBuscasRecentes") { value(false) }
            jsonPath("$.buscasRecentes.length()") { value(0) }
        }

        em.flush()
        em.clear()

        mockMvc.get("/api/me") {
            header("Authorization", authorization)
        }.andExpect {
            status { isOk() }

            jsonPath("$.ubsReferencia") { value(null) }
            jsonPath("$.salvarBuscasRecentes") { value(false) }
            jsonPath("$.buscasRecentes.length()") { value(0) }
        }

        assertTrue(
            buscas.listarPorUsuario(
                requireNotNull(cidadao.id),
            ).isEmpty(),
        )
    }

    @Test
    fun `atualizacoes sucessivas substituem o historico anterior`() {
        val cidadao = criarUsuario("perfil-repetidas@teste.com")
        val usuarioId = requireNotNull(cidadao.id)
        val authorization = "Bearer ${token(cidadao)}"

        // Primeira atualização: duas buscas recentes.
        mockMvc.put("/api/me") {
            header("Authorization", authorization)
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao(
                buscasJson = """["Metformina", "Losartana"]""",
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.buscasRecentes.length()") { value(2) }
            jsonPath("$.buscasRecentes[0]") {
                value("Metformina")
            }
            jsonPath("$.buscasRecentes[1]") {
                value("Losartana")
            }
        }

        // Os testes compartilham uma transação externa.
        // Flush e clear simulam contextos de persistência
        // separados, como ocorre entre requisições reais.
        em.flush()
        em.clear()

        // Segunda atualização: substitui as buscas anteriores.
        mockMvc.put("/api/me") {
            header("Authorization", authorization)
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao(
                nome = "Rita Atualizada",
                buscasJson = """["Dipirona", "Paracetamol"]""",
            )
        }.andExpect {
            status { isOk() }

            jsonPath("$.nome") { value("Rita Atualizada") }
            jsonPath("$.buscasRecentes.length()") { value(2) }
            jsonPath("$.buscasRecentes[0]") {
                value("Dipirona")
            }
            jsonPath("$.buscasRecentes[1]") {
                value("Paracetamol")
            }
        }

        // Confere o estado real do banco, sem entidades
        // anteriormente carregadas no EntityManager.
        em.flush()
        em.clear()

        val termosPersistidos = buscas.listarPorUsuario(usuarioId)
            .map { it.termo }

        assertEquals(
            listOf("Dipirona", "Paracetamol"),
            termosPersistidos,
        )

        // GET deve refletir somente o histórico mais recente.
        mockMvc.get("/api/me") {
            header("Authorization", authorization)
        }.andExpect {
            status { isOk() }

            jsonPath("$.nome") { value("Rita Atualizada") }
            jsonPath("$.buscasRecentes.length()") { value(2) }
            jsonPath("$.buscasRecentes[0]") {
                value("Dipirona")
            }
            jsonPath("$.buscasRecentes[1]") {
                value("Paracetamol")
            }
        }
    }

    @Test
    fun `cada cidadao acessa somente o proprio perfil`() {
        val primeiro = criarUsuario("perfil-primeiro@teste.com")
        val segundo = criarUsuario("perfil-segundo@teste.com")

        val authorizationPrimeiro = "Bearer ${token(primeiro)}"
        val authorizationSegundo = "Bearer ${token(segundo)}"

        mockMvc.put("/api/me") {
            header("Authorization", authorizationPrimeiro)
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao(
                nome = "Primeiro Cidadão",
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.nome") { value("Primeiro Cidadão") }
        }

        em.flush()
        em.clear()

        mockMvc.get("/api/me") {
            header("Authorization", authorizationSegundo)
        }.andExpect {
            status { isOk() }

            jsonPath("$.id") {
                value(requireNotNull(segundo.id).toInt())
            }
            jsonPath("$.nome") { value("Cidadão Teste") }
            jsonPath("$.buscasRecentes.length()") { value(0) }
        }

        mockMvc.get("/api/me") {
            header("Authorization", authorizationPrimeiro)
        }.andExpect {
            status { isOk() }

            jsonPath("$.id") {
                value(requireNotNull(primeiro.id).toInt())
            }
            jsonPath("$.nome") { value("Primeiro Cidadão") }
            jsonPath("$.buscasRecentes.length()") { value(2) }
        }
    }

    @Test
    fun `rejeita acesso sem token e acesso de gestor`() {
        mockMvc.get("/api/me").andExpect {
            status { isUnauthorized() }
        }

        mockMvc.put("/api/me") {
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao()
        }.andExpect {
            status { isUnauthorized() }
        }

        val gestor = criarUsuario(
            "perfil-gestor@teste.com",
            PapelUsuario.GESTOR,
        )

        val authorization = "Bearer ${token(gestor)}"

        mockMvc.get("/api/me") {
            header("Authorization", authorization)
        }.andExpect {
            status { isForbidden() }
        }

        mockMvc.put("/api/me") {
            header("Authorization", authorization)
            contentType = MediaType.APPLICATION_JSON
            content = corpoAtualizacao()
        }.andExpect {
            status { isForbidden() }
        }
    }
}
