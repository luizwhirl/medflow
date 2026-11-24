package br.ufal.medflow.aviso

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.estoque.ItemEstoque
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.remessa.EtapaRemessa
import br.ufal.medflow.remessa.Remessa
import br.ufal.medflow.remessa.RemessaEvento
import br.ufal.medflow.remessa.RemessaEventoRepository
import br.ufal.medflow.remessa.RemessaRepository
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Avisos montados a partir do estoque, das remessas e da sincronização reais no PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AvisoIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val ubsRepository: UbsRepository,
    @Autowired private val medicamentoRepository: MedicamentoRepository,
    @Autowired private val itemEstoqueRepository: ItemEstoqueRepository,
    @Autowired private val remessaRepository: RemessaRepository,
    @Autowired private val remessaEventoRepository: RemessaEventoRepository,
) {

    private val agora = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    private fun ubs(ultimaSincronizacao: Instant?) = ubsRepository.saveAndFlush(
        Ubs(
            cnes = "7980001", nome = "UBS Avisos", tipo = TipoUnidade.UBS, bairro = "Centro", endereco = "Rua A, 1",
            codigoIbgeMunicipio = "2704302", latitude = -9.6, longitude = -35.7,
            ultimaSincronizacao = ultimaSincronizacao,
        ),
    )

    private fun med(catmat: String, nome: String) = medicamentoRepository.saveAndFlush(
        Medicamento(catmat = catmat, nome = nome, concentracao = "850 mg", formaFarmaceutica = "Comprimido",
            categoria = "Diabetes"),
    )

    @Test
    fun `junta disponivel, remessa em andamento e sincronizacao atrasada, sem login`() {
        val unidade = ubs(agora.minus(3, ChronoUnit.DAYS))
        val metformina = med("BR7980001", "Metformina")
        val insulina = med("BR7980002", "Insulina NPH")
        val naoAcompanhado = med("BR7980003", "Captopril")

        itemEstoqueRepository.saveAndFlush(
            ItemEstoque(unidade, metformina, StatusEstoque.DISPONIVEL, 120, agora.minus(1, ChronoUnit.HOURS)),
        )
        itemEstoqueRepository.saveAndFlush(ItemEstoque(unidade, naoAcompanhado, StatusEstoque.DISPONIVEL, 50, agora))

        val remessa = remessaRepository.saveAndFlush(
            Remessa(unidade, insulina, 40, LocalDate.now().plusDays(2), agora.minus(2, ChronoUnit.DAYS)),
        )
        remessaEventoRepository.saveAndFlush(RemessaEvento(remessa, EtapaRemessa.PEDIDO, agora.minus(2, ChronoUnit.DAYS)))
        remessaEventoRepository.saveAndFlush(
            RemessaEvento(remessa, EtapaRemessa.EM_TRANSPORTE, agora.minus(30, ChronoUnit.MINUTES)),
        )
        val concluida = remessaRepository.saveAndFlush(
            Remessa(unidade, metformina, 10, LocalDate.now().minusDays(1), agora.minus(5, ChronoUnit.DAYS)),
        )
        remessaEventoRepository.saveAndFlush(RemessaEvento(concluida, EtapaRemessa.DISPONIVEL, agora.minus(4, ChronoUnit.DAYS)))

        mockMvc.get("/api/avisos") {
            param("ubsId", unidade.id.toString())
            param("medicamentoIds", "${metformina.id},${insulina.id}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].id") { value("remessa-${remessa.id}-EM_TRANSPORTE") }
            jsonPath("$[0].tipo") { value("REMESSA") }
            jsonPath("$[0].medicamentoNome") { value("Insulina NPH") }
            jsonPath("$[0].previsaoChegada") { value(LocalDate.now().plusDays(2).toString()) }
            jsonPath("$[1].id") { value("disponivel-${unidade.id}-${metformina.id}") }
            jsonPath("$[1].quantidade") { value(120) }
            jsonPath("$[2].tipo") { value("SINCRONIZACAO_ATRASADA") }
        }
    }

    @Test
    fun `ubs inexistente retorna 404`() {
        mockMvc.get("/api/avisos") { param("ubsId", "999999") }.andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Recurso não encontrado") }
        }
    }
}
