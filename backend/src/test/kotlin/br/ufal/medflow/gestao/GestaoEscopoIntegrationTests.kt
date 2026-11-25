package br.ufal.medflow.gestao

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.auth.JwtService
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Perfil "dev": Seu Carlos é gestor da UBS 9000101 e Dona Marta é gestora sem vínculo. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev", "demo")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class GestaoEscopoIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val jwtService: JwtService,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private fun token(email: String) = jwtService.emitir(requireNotNull(usuarios.findByEmail(email))).token

    private fun idUbs(cnes: String) = jdbc.queryForObject("SELECT id FROM ubs WHERE cnes = ?", Long::class.java, cnes)!!

    private fun escopo(email: String?, ubsId: String? = null): ResultActionsDsl =
        mockMvc.get("/api/gestao/escopo") {
            email?.let { header("Authorization", "Bearer ${token(it)}") }
            ubsId?.let { param("ubsId", it) }
        }

    @Test
    fun `seed vincula o Carlos a UBS 9000101 e deixa a Marta sem vinculo`() {
        assertEquals(idUbs("9000101"), usuarios.findUbsGestaoId(requireNotNull(usuarios.findByEmail("carlos@medflow.local")?.id)))
        assertEquals(null, usuarios.findUbsGestaoId(requireNotNull(usuarios.findByEmail("marta@medflow.local")?.id)))
    }

    @Test
    fun `gestor com vinculo ve so a UBS dele`() {
        escopo("carlos@medflow.local").andExpect {
            status { isOk() }
            jsonPath("$.restrito") { value(true) }
            jsonPath("$.ubs.cnes") { value("9000101") }
            jsonPath("$.ubs.id") { value(idUbs("9000101")) }
        }
    }

    @Test
    fun `gestor com vinculo pode pedir a propria UBS`() {
        escopo("carlos@medflow.local", idUbs("9000101").toString()).andExpect {
            status { isOk() }
            jsonPath("$.ubs.cnes") { value("9000101") }
        }
    }

    @Test
    fun `gestor com vinculo pedindo outra UBS recebe 403 no formato problem json`() {
        escopo("carlos@medflow.local", idUbs("9000102").toString()).andExpect {
            status { isForbidden() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.title") { value("Acesso negado") }
        }
    }

    @Test
    fun `gestor sem vinculo ve a rede toda`() {
        escopo("marta@medflow.local").andExpect {
            status { isOk() }
            jsonPath("$.restrito") { value(false) }
            jsonPath("$.ubs") { value(nullValue()) }
        }
    }

    @Test
    fun `gestor sem vinculo pode filtrar por qualquer UBS`() {
        escopo("marta@medflow.local", idUbs("9000102").toString()).andExpect {
            status { isOk() }
            jsonPath("$.restrito") { value(false) }
            jsonPath("$.ubs.cnes") { value("9000102") }
        }
    }

    @Test
    fun `UBS inexistente retorna 404 e id invalido retorna 400`() {
        escopo("marta@medflow.local", "999999").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
        escopo("marta@medflow.local", "0").andExpect { status { isBadRequest() } }
        escopo("marta@medflow.local", "abc").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `cidadao recebe 403 e sem token recebe 401`() {
        escopo("rita@medflow.local").andExpect { status { isForbidden() } }
        escopo(null).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `token de gestor que foi apagado nao ve a rede`() {
        val gestor = usuarios.saveAndFlush(Usuario("Gestor Temporário", "temporario@teste.com", "hash", PapelUsuario.GESTOR))
        val jwt = jwtService.emitir(gestor).token
        jdbc.update("DELETE FROM usuario WHERE id = ?", gestor.id)

        mockMvc.get("/api/gestao/escopo") { header("Authorization", "Bearer $jwt") }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `so gestor pode ter vinculo com UBS`() {
        val rita = requireNotNull(usuarios.findByEmail("rita@medflow.local")?.id)

        assertFailsWith<DataIntegrityViolationException> {
            jdbc.update("UPDATE usuario SET ubs_gestao_id = ? WHERE id = ?", idUbs("9000101"), rita)
        }
    }

    @Test
    fun `UBS com gestor vinculado nao pode ser apagada, e sem vinculo pode`() {
        // UBS sem estoque nem outra referência: o único motivo para barrar a exclusão é o vínculo do gestor
        jdbc.update(
            "INSERT INTO ubs (cnes, nome, tipo, bairro, endereco, codigo_ibge_municipio, latitude, longitude) " +
                "VALUES ('9099999', 'UBS Teste', 'UBS', 'Centro', 'Rua A, 1', '2704302', -9.6, -35.7)",
        )
        val ubs = idUbs("9099999")
        val gestor = usuarios.saveAndFlush(Usuario("Gestor Temporário", "temporario@teste.com", "hash", PapelUsuario.GESTOR))
        jdbc.update("UPDATE usuario SET ubs_gestao_id = ? WHERE id = ?", ubs, gestor.id)

        jdbc.execute("SAVEPOINT antes_de_apagar")
        assertFailsWith<DataIntegrityViolationException> { jdbc.update("DELETE FROM ubs WHERE id = ?", ubs) }
        jdbc.execute("ROLLBACK TO SAVEPOINT antes_de_apagar")

        jdbc.update("UPDATE usuario SET ubs_gestao_id = NULL WHERE id = ?", gestor.id)
        assertEquals(1, jdbc.update("DELETE FROM ubs WHERE id = ?", ubs))
    }

    @Test
    fun `gestor com vinculo pedindo UBS inexistente recebe 403 e nao 404`() {
        // validar a existência antes do vínculo revelaria quais ids existem
        escopo("carlos@medflow.local", "999999").andExpect {
            status { isForbidden() }
            jsonPath("$.title") { value("Acesso negado") }
        }
    }

    @Test
    fun `conta que deixou de ser gestora nao ve o painel com o token antigo`() {
        val gestor = usuarios.saveAndFlush(Usuario("Rebaixado", "rebaixado@teste.com", "hash", PapelUsuario.GESTOR))
        val jwt = jwtService.emitir(gestor).token
        jdbc.update("UPDATE usuario SET papel = 'CIDADAO' WHERE id = ?", gestor.id)

        mockMvc.get("/api/gestao/escopo") { header("Authorization", "Bearer $jwt") }
            .andExpect { status { isUnauthorized() } }
    }

    private fun gestorSemVinculo(email: String) =
        usuarios.saveAndFlush(Usuario("Gestor", email, "hash", PapelUsuario.GESTOR))

    private fun novaUbs(cnes: String): Long {
        jdbc.update(
            "INSERT INTO ubs (cnes, nome, tipo, bairro, endereco, codigo_ibge_municipio, latitude, longitude) " +
                "VALUES (?, 'UBS Vínculo', 'UBS', 'Centro', 'Rua A, 1', '9999999', -9.6, -35.7)",
            cnes,
        )
        return idUbs(cnes)
    }

    @Test
    fun `vincularGestor liga pelo CNES e devolve 1`() {
        val gestor = gestorSemVinculo("vincula@teste.com")
        val ubs = novaUbs("9980001")

        assertEquals(1, usuarios.vincularGestor("vincula@teste.com", "9980001"))
        assertEquals(ubs, usuarios.findUbsGestaoId(requireNotNull(gestor.id)))
    }

    @Test
    fun `CNES inexistente nao muda o vinculo e o gestor continua sem ver a rede por engano`() {
        val semVinculo = gestorSemVinculo("sem-vinculo@teste.com")
        val comVinculo = gestorSemVinculo("com-vinculo@teste.com")
        val ubs = novaUbs("9980002")
        usuarios.vincularGestor("com-vinculo@teste.com", "9980002")

        assertEquals(0, usuarios.vincularGestor("sem-vinculo@teste.com", "0000000"))
        assertEquals(0, usuarios.vincularGestor("com-vinculo@teste.com", "0000000"))

        assertEquals(null, usuarios.findUbsGestaoId(requireNotNull(semVinculo.id)))
        assertEquals(ubs, usuarios.findUbsGestaoId(requireNotNull(comVinculo.id))) // não foi para NULL
    }

    @Test
    fun `vincularGestor nao troca o vinculo de quem ja tem e so mexe em gestor`() {
        val gestor = gestorSemVinculo("ja-vinculado@teste.com")
        val primeira = novaUbs("9980003")
        novaUbs("9980004")
        usuarios.vincularGestor("ja-vinculado@teste.com", "9980003")

        assertEquals(0, usuarios.vincularGestor("ja-vinculado@teste.com", "9980004"))
        assertEquals(primeira, usuarios.findUbsGestaoId(requireNotNull(gestor.id)))
        assertEquals(0, usuarios.vincularGestor("rita@medflow.local", "9980003")) // cidadã
    }
}
