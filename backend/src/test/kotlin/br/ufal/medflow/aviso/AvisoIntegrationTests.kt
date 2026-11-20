package br.ufal.medflow.aviso

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.auth.JwtService
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Testes de integração da API de avisos com JWT e PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AvisoIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val avisos: AvisoRepository,
    @Autowired private val jwtService: JwtService,
    @Autowired private val entityManager: EntityManager,
) {

    private fun criarUsuario(
        email: String,
        papel: PapelUsuario = PapelUsuario.CIDADAO,
    ): Usuario =
        usuarios.saveAndFlush(
            Usuario(
                nome = "Usuário Teste",
                email = email,
                senhaHash = "hash-teste",
                papel = papel,
            ),
        )

    private fun token(usuario: Usuario): String =
        jwtService.emitir(usuario).token

    private fun criarAviso(
        usuario: Usuario,
        chaveEvento: String,
        criadoEm: Instant,
        tipo: TipoAviso = TipoAviso.MEDICAMENTO_DISPONIVEL,
        lido: Boolean = false,
    ): Aviso =
        avisos.saveAndFlush(
            Aviso(
                usuario = usuario,
                tipo = tipo,
                titulo = "Aviso de teste",
                texto = "Informação para o cidadão.",
                lido = lido,
                criadoEm = criadoEm,
                chaveEvento = chaveEvento,
            ),
        )

    @Test
    fun `lista apenas avisos do cidadao autenticado em ordem decrescente`() {
        val cidadao = criarUsuario("avisos-listagem@teste.com")
        val outro = criarUsuario("avisos-outro@teste.com")

        val antigo = criarAviso(
            cidadao,
            "listagem-antigo",
            Instant.parse("2026-11-18T10:00:00Z"),
            tipo = TipoAviso.UBS_DADOS_ATRASADOS,
            lido = true,
        )

        val recente = criarAviso(
            cidadao,
            "listagem-recente",
            Instant.parse("2026-11-19T20:00:00Z"),
        )

        criarAviso(
            outro,
            "listagem-outro",
            Instant.parse("2026-11-19T21:00:00Z"),
        )

        mockMvc.get("/api/avisos") {
            header("Authorization", "Bearer ${token(cidadao)}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }

            jsonPath("$[0].id") { value(recente.id!!.toInt()) }
            jsonPath("$[0].tipo") {
                value("MEDICAMENTO_DISPONIVEL")
            }
            jsonPath("$[0].lido") { value(false) }

            jsonPath("$[1].id") { value(antigo.id!!.toInt()) }
            jsonPath("$[1].tipo") {
                value("UBS_DADOS_ATRASADOS")
            }
            jsonPath("$[1].lido") { value(true) }
        }
    }

    @Test
    fun `retorna lista vazia quando cidadao nao possui avisos`() {
        val cidadao = criarUsuario("avisos-vazio@teste.com")

        mockMvc.get("/api/avisos") {
            header("Authorization", "Bearer ${token(cidadao)}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `marca apenas avisos do cidadao e nao repete atualizacao`() {
        val cidadao = criarUsuario("avisos-patch@teste.com")
        val outro = criarUsuario("avisos-patch-outro@teste.com")
        val data = Instant.parse("2026-11-19T10:00:00Z")

        val primeiro = criarAviso(
            cidadao,
            "patch-primeiro",
            data,
        )

        val segundo = criarAviso(
            cidadao,
            "patch-segundo",
            data,
        )

        val jaLido = criarAviso(
            cidadao,
            "patch-ja-lido",
            data,
            lido = true,
        )

        val avisoDoOutro = criarAviso(
            outro,
            "patch-outro",
            data,
        )

        val authorization = "Bearer ${token(cidadao)}"

        mockMvc.patch("/api/avisos/lidas") {
            header("Authorization", authorization)
        }.andExpect {
            status { isOk() }
            jsonPath("$.atualizados") { value(2) }
        }

        mockMvc.patch("/api/avisos/lidas") {
            header("Authorization", authorization)
        }.andExpect {
            status { isOk() }
            jsonPath("$.atualizados") { value(0) }
        }

        // O UPDATE JPQL não atualiza automaticamente entidades
        // que já estão presentes no contexto de persistência.
        entityManager.clear()

        assertTrue(avisos.findById(primeiro.id!!).orElseThrow().lido)
        assertTrue(avisos.findById(segundo.id!!).orElseThrow().lido)
        assertTrue(avisos.findById(jaLido.id!!).orElseThrow().lido)
        assertFalse(avisos.findById(avisoDoOutro.id!!).orElseThrow().lido)
    }

    @Test
    fun `rejeita requisicoes sem autenticacao`() {
        mockMvc.get("/api/avisos").andExpect {
            status { isUnauthorized() }
        }

        mockMvc.patch("/api/avisos/lidas").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `gestor nao pode acessar endpoints de avisos`() {
        val gestor = criarUsuario(
            "gestor-avisos@teste.com",
            PapelUsuario.GESTOR,
        )

        val authorization = "Bearer ${token(gestor)}"

        mockMvc.get("/api/avisos") {
            header("Authorization", authorization)
        }.andExpect {
            status { isForbidden() }
        }

        mockMvc.patch("/api/avisos/lidas") {
            header("Authorization", authorization)
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `usuario excluido perde acesso e seus avisos sao removidos`() {
        val cidadao = criarUsuario("avisos-excluido@teste.com")
        val authorization = "Bearer ${token(cidadao)}"

        val aviso = criarAviso(
            cidadao,
            "usuario-excluido",
            Instant.parse("2026-11-19T10:00:00Z"),
        )

        val usuarioId = cidadao.id!!
        val avisoId = aviso.id!!

        // Garante que todos os dados foram gravados antes
        // de testar a exclusão em cascata do PostgreSQL.
        entityManager.flush()
        entityManager.clear()

        // Executa o DELETE diretamente no banco para verificar
        // a constraint ON DELETE CASCADE definida na migration V8.
        val excluidos = entityManager
            .createNativeQuery(
                "DELETE FROM usuario WHERE id = :usuarioId",
            )
            .setParameter("usuarioId", usuarioId)
            .executeUpdate()

        assertEquals(1, excluidos)

        // Descarta entidades que possam estar desatualizadas
        // após a exclusão executada diretamente no PostgreSQL.
        entityManager.clear()

        // O PostgreSQL deve excluir automaticamente o aviso.
        assertFalse(avisos.existsById(avisoId))
        assertFalse(usuarios.existsById(usuarioId))

        // O token emitido antes da exclusão não deve
        // permitir operações de uma conta inexistente.
        mockMvc.get("/api/avisos") {
            header("Authorization", authorization)
        }.andExpect {
            status { isUnauthorized() }
        }

        mockMvc.patch("/api/avisos/lidas") {
            header("Authorization", authorization)
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
