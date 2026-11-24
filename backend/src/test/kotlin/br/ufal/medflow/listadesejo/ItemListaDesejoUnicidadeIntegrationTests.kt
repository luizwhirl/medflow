package br.ufal.medflow.listadesejo

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import kotlin.test.assertFailsWith

/** A V14 garante no banco a unicidade por aparelho, UBS e medicamento (sem depender do serviço). */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ItemListaDesejoUnicidadeIntegrationTests(
    @Autowired private val itens: ItemListaDesejoRepository,
    @Autowired private val ubsRepository: UbsRepository,
    @Autowired private val medicamentoRepository: MedicamentoRepository,
) {

    private val unidade by lazy {
        ubsRepository.saveAndFlush(
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
    }

    private val medicamento by lazy {
        medicamentoRepository.saveAndFlush(
            Medicamento(
                catmat = "BR9990001",
                nome = "Medicamento Teste",
                concentracao = "10 mg",
                formaFarmaceutica = "Comprimido",
                categoria = "Teste",
            ),
        )
    }

    private fun item(aparelho: UUID) =
        ItemListaDesejo(dispositivoId = aparelho, ubs = unidade, medicamento = medicamento)

    @Test
    fun `aparelhos diferentes podem ter o mesmo medicamento na mesma UBS`() {
        itens.saveAndFlush(item(UUID.randomUUID()))
        itens.saveAndFlush(item(UUID.randomUUID()))
    }

    @Test
    fun `o banco recusa o mesmo item duas vezes no mesmo aparelho`() {
        val aparelho = UUID.randomUUID()
        itens.saveAndFlush(item(aparelho))

        assertFailsWith<DataIntegrityViolationException> { itens.saveAndFlush(item(aparelho)) }
    }
}
