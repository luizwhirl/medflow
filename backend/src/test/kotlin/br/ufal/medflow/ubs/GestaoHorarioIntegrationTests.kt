package br.ufal.medflow.ubs

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.auth.JwtService
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.nullValue
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

/** Cadastro do horário pela gestão (`PUT /api/gestao/ubs/{id}/horarios`), com banco vazio (perfil "test"). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class GestaoHorarioIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val ubsRepository: UbsRepository,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val jwtService: JwtService,
) {

    private fun ubs() = ubsRepository.saveAndFlush(
        Ubs(
            cnes = "9999801",
            nome = "UBS Cadastro de Horário",
            tipo = TipoUnidade.UBS,
            bairro = "Centro",
            endereco = "Rua C, 3",
            codigoIbgeMunicipio = "2704302",
            latitude = -9.66,
            longitude = -35.73,
        ),
    ).id!!

    private fun token(papel: PapelUsuario): String {
        val conta = usuarios.saveAndFlush(Usuario("Conta", "${papel.name.lowercase()}@teste.com", "hash", papel))
        return jwtService.emitir(conta).token
    }

    private fun cadastrar(ubsId: Long, json: String, token: String? = token(PapelUsuario.GESTOR)) =
        mockMvc.put("/api/gestao/ubs/$ubsId/horarios") {
            contentType = MediaType.APPLICATION_JSON
            content = json
            token?.let { header("Authorization", "Bearer $it") }
        }

    private val segundaASexta = """
        {"turnos": [
          {"diaSemana": "SEGUNDA", "abre": "07:00", "fecha": "17:00"},
          {"diaSemana": "TERCA", "abre": "07:00", "fecha": "17:00"}
        ]}
    """

    @Test
    fun `gestor cadastra os turnos e o detalhe da UBS passa a mostrar o horario`() {
        val id = ubs()

        cadastrar(id, segundaASexta).andExpect {
            status { isOk() }
            jsonPath("$.ubsId") { value(id) }
            jsonPath("$.horarios[*].diaSemana") { value(containsInAnyOrder("SEGUNDA", "TERCA")) }
            jsonPath("$.horarios[0].abre") { value("07:00") }
        }

        mockMvc.get("/api/ubs/$id").andExpect {
            status { isOk() }
            jsonPath("$.horarios.length()") { value(2) }
        }
    }

    @Test
    fun `novo cadastro substitui todos os turnos anteriores`() {
        val id = ubs()
        cadastrar(id, segundaASexta).andExpect { status { isOk() } }

        cadastrar(id, """{"turnos": [{"diaSemana": "SABADO", "abre": "08:00", "fecha": "12:00"}]}""").andExpect {
            status { isOk() }
            jsonPath("$.horarios.length()") { value(1) }
            jsonPath("$.horarios[0].diaSemana") { value("SABADO") }
        }
    }

    @Test
    fun `lista vazia apaga o horario e a situacao volta a ser desconhecida`() {
        val id = ubs()
        cadastrar(id, segundaASexta).andExpect { status { isOk() } }

        cadastrar(id, """{"turnos": []}""").andExpect {
            status { isOk() }
            jsonPath("$.horarios") { isEmpty() }
            jsonPath("$.abertaAgora") { value(nullValue()) }
        }
        mockMvc.get("/api/ubs/$id").andExpect { jsonPath("$.abertaAgora") { value(nullValue()) } }
    }

    @Test
    fun `turno de 24 horas e turno que vira a noite sao aceitos`() {
        val id = ubs()

        cadastrar(
            id,
            """{"turnos": [
              {"diaSemana": "DOMINGO", "abre": "00:00", "fecha": "00:00"},
              {"diaSemana": "SEXTA", "abre": "19:00", "fecha": "07:00"}
            ]}""",
        ).andExpect {
            status { isOk() }
            jsonPath("$.horarios.length()") { value(2) }
        }
    }

    @Test
    fun `sem token retorna 401 e cidadao retorna 403`() {
        val id = ubs()

        cadastrar(id, segundaASexta, token = null).andExpect { status { isUnauthorized() } }
        cadastrar(id, segundaASexta, token = token(PapelUsuario.CIDADAO)).andExpect { status { isForbidden() } }
    }

    @Test
    fun `UBS inexistente retorna 404 no formato problem json`() {
        cadastrar(999999, segundaASexta).andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
    }

    @Test
    fun `turno repetido, hora invalida, dia ausente ou sem lista retornam 400`() {
        val id = ubs()

        cadastrar(
            id,
            """{"turnos": [
              {"diaSemana": "SEGUNDA", "abre": "07:00", "fecha": "12:00"},
              {"diaSemana": "SEGUNDA", "abre": "07:00", "fecha": "17:00"}
            ]}""",
        ).andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("Parâmetro inválido") }
        }
        cadastrar(id, """{"turnos": [{"diaSemana": "SEGUNDA", "abre": "25:00", "fecha": "17:00"}]}""")
            .andExpect { status { isBadRequest() } }
        cadastrar(id, """{"turnos": [{"abre": "07:00", "fecha": "17:00"}]}""")
            .andExpect { status { isBadRequest() } }
        cadastrar(id, """{}""").andExpect { status { isBadRequest() } }
    }
}
