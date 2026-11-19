package br.ufal.medflow.listadesejo

import br.ufal.medflow.TestcontainersConfiguration
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
import org.springframework.dao.DataIntegrityViolationException
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
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/** Lista de desejos sem login: o dono é o aparelho do cabeçalho X-Dispositivo-Id. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ItemListaDesejoDispositivoIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val itens: ItemListaDesejoRepository,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val ubsRepository: UbsRepository,
    @Autowired private val medicamentoRepository: MedicamentoRepository,
    @Autowired private val jwtService: JwtService,
    @Autowired private val senhas: PasswordEncoder,
) {

    private val aparelhoA = "3f1c2b7e-8a4d-4e1f-9b2c-5d6e7f8a9b0c"
    private val aparelhoB = "a0b1c2d3-e4f5-4a6b-8c7d-9e0f1a2b3c4d"

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

    private fun criar(aparelho: String, ubsId: Long, medicamentoId: Long) =
        mockMvc.post("/api/lista-desejos") {
            header(ItemListaDesejoController.CABECALHO_DISPOSITIVO, aparelho)
            contentType = MediaType.APPLICATION_JSON
            content = """{"ubsId":$ubsId,"medicamentoId":$medicamentoId}"""
        }

    private fun listar(aparelho: String) =
        mockMvc.get("/api/lista-desejos") {
            header(ItemListaDesejoController.CABECALHO_DISPOSITIVO, aparelho)
        }

    private fun extrairId(json: String): Long =
        assertNotNull(Regex("\"id\":(\\d+)").find(json)).groupValues[1].toLong()

    @Test
    fun `aparelho cria, lista, desliga o aviso e remove sem login`() {
        val unidade = requireNotNull(ubs().id)
        val remedio = requireNotNull(medicamento().id)

        val resposta = criar(aparelhoA, unidade, remedio).andExpect {
            status { isCreated() }
            jsonPath("$.ubsNome") { value("UBS Teste") }
            jsonPath("$.medicamentoNome") { value("Metformina") }
            jsonPath("$.notificar") { value(true) }
        }.andReturn().response.contentAsString
        val itemId = extrairId(resposta)

        listar(aparelhoA).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(itemId) }
        }

        mockMvc.patch("/api/lista-desejos/$itemId") {
            header(ItemListaDesejoController.CABECALHO_DISPOSITIVO, aparelhoA)
            contentType = MediaType.APPLICATION_JSON
            content = """{"notificar":false}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.notificar") { value(false) }
        }

        mockMvc.delete("/api/lista-desejos/$itemId") {
            header(ItemListaDesejoController.CABECALHO_DISPOSITIVO, aparelhoA)
        }.andExpect {
            status { isNoContent() }
        }

        listar(aparelhoA).andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `um aparelho nao ve nem altera itens de outro`() {
        val unidade = requireNotNull(ubs().id)
        val remedio = requireNotNull(medicamento().id)
        val itemId = extrairId(
            criar(aparelhoA, unidade, remedio).andReturn().response.contentAsString,
        )

        listar(aparelhoB).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }

        mockMvc.patch("/api/lista-desejos/$itemId") {
            header(ItemListaDesejoController.CABECALHO_DISPOSITIVO, aparelhoB)
            contentType = MediaType.APPLICATION_JSON
            content = """{"notificar":false}"""
        }.andExpect {
            status { isNotFound() }
        }

        mockMvc.delete("/api/lista-desejos/$itemId") {
            header(ItemListaDesejoController.CABECALHO_DISPOSITIVO, aparelhoB)
        }.andExpect {
            status { isNotFound() }
        }

        listar(aparelhoA).andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].notificar") { value(true) }
        }
    }

    @Test
    fun `duplicata no mesmo aparelho retorna 409, mas outro aparelho pode ter o mesmo item`() {
        val unidade = requireNotNull(ubs().id)
        val remedio = requireNotNull(medicamento().id)

        criar(aparelhoA, unidade, remedio).andExpect { status { isCreated() } }
        criar(aparelhoA, unidade, remedio).andExpect {
            status { isConflict() }
            jsonPath("$.title") { value("Item já cadastrado") }
        }
        criar(aparelhoB, unidade, remedio).andExpect { status { isCreated() } }
    }

    @Test
    fun `identificador fora do formato UUID retorna 400`() {
        for (invalido in listOf("abc", "1-1-1-1-1", "", "3f1c2b7e8a4d4e1f9b2c5d6e7f8a9b0c")) {
            listar(invalido).andExpect {
                status { isBadRequest() }
                jsonPath("$.title") { value("Parâmetro inválido") }
            }
        }
    }

    @Test
    fun `com token de cidadao a lista e da conta, mesmo enviando o aparelho`() {
        val unidade = requireNotNull(ubs().id)
        val remedio = requireNotNull(medicamento().id)
        criar(aparelhoA, unidade, remedio).andExpect { status { isCreated() } }

        val rita = usuarios.saveAndFlush(
            Usuario(
                nome = "Rita",
                email = "rita-aparelho@teste.com",
                senhaHash = requireNotNull(senhas.encode("senha-teste-123")),
                papel = PapelUsuario.CIDADAO,
            ),
        )

        mockMvc.get("/api/lista-desejos") {
            header("Authorization", "Bearer ${jwtService.emitir(rita).token}")
            header(ItemListaDesejoController.CABECALHO_DISPOSITIVO, aparelhoA)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `o banco exige exatamente um dono por item`() {
        val unidade = ubs()
        val remedio = medicamento()

        assertFailsWith<DataIntegrityViolationException> {
            itens.saveAndFlush(ItemListaDesejo(ubs = unidade, medicamento = remedio))
        }
    }

    @Test
    fun `o banco nao aceita conta e aparelho no mesmo item`() {
        val rita = usuarios.saveAndFlush(
            Usuario(
                nome = "Rita",
                email = "rita-dois-donos@teste.com",
                senhaHash = requireNotNull(senhas.encode("senha-teste-123")),
                papel = PapelUsuario.CIDADAO,
            ),
        )

        assertFailsWith<DataIntegrityViolationException> {
            itens.saveAndFlush(
                ItemListaDesejo(
                    usuario = rita,
                    dispositivoId = UUID.fromString(aparelhoA),
                    ubs = ubs(),
                    medicamento = medicamento(),
                ),
            )
        }
    }
}
