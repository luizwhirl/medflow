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
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers

/** Garante que a unicidade da lista de desejos é por cidadão. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ItemListaDesejoUnicidadeIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val ubsRepository: UbsRepository,
    @Autowired private val medicamentoRepository: MedicamentoRepository,
    @Autowired private val jwtService: JwtService,
    @Autowired private val senhas: PasswordEncoder,
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

    @Test
    fun `cidadaos diferentes podem desejar mesmo medicamento na mesma UBS`() {
        val rita = cidadao("rita-unicidade@teste.com")
        val carlos = cidadao("carlos-unicidade@teste.com")

        val unidade = ubsRepository.saveAndFlush(
            Ubs(
                cnes = "9000201",
                nome = "UBS Unicidade",
                tipo = TipoUnidade.UBS,
                bairro = "Centro",
                endereco = "Rua B, 2",
                codigoIbgeMunicipio = "2704302",
                latitude = -9.5,
                longitude = -35.7,
            ),
        )

        val medicamento = medicamentoRepository.saveAndFlush(
            Medicamento(
                catmat = "BR9990001",
                nome = "Medicamento Teste",
                concentracao = "10 mg",
                formaFarmaceutica = "Comprimido",
                categoria = "Teste",
            ),
        )

        val ubsId = requireNotNull(unidade.id)
        val medicamentoId = requireNotNull(medicamento.id)

        val tokenRita = jwtService.emitir(rita).token
        val tokenCarlos = jwtService.emitir(carlos).token

        for (token in listOf(tokenRita, tokenCarlos)) {
            mockMvc.post("/api/lista-desejos") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"ubsId":$ubsId,"medicamentoId":$medicamentoId}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.ubsId") { value(ubsId) }
                jsonPath("$.medicamentoId") { value(medicamentoId) }
            }
        }

        for (token in listOf(tokenRita, tokenCarlos)) {
            mockMvc.get("/api/lista-desejos") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].medicamentoId") { value(medicamentoId) }
            }
        }
    }
}
