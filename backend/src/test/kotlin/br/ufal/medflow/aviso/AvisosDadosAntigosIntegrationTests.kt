package br.ufal.medflow.aviso

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.integracao.FonteEstoque
import br.ufal.medflow.integracao.PosicaoEstoque
import br.ufal.medflow.listadesejo.ItemListaDesejo
import br.ufal.medflow.listadesejo.ItemListaDesejoRepository
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.sincronizacao.SincronizacaoEstoqueService
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/** Impede que respostas antigas da fonte revertam a disponibilidade. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AvisosDadosAntigosIntegrationTests(
    @Autowired private val sincronizacao: SincronizacaoEstoqueService,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val unidades: UbsRepository,
    @Autowired private val medicamentos: MedicamentoRepository,
    @Autowired private val listaDesejos: ItemListaDesejoRepository,
    @Autowired private val itens: ItemEstoqueRepository,
    @Autowired private val avisos: AvisoRepository,
    @Autowired private val em: EntityManager,
) {

    @MockitoBean
    private lateinit var fonte: FonteEstoque

    private val ibge = "2702405"
    private val cnes = "9876541"
    private val dataRecente = LocalDate.of(2026, 11, 19)
    private val dataAntiga = LocalDate.of(2026, 11, 18)

    private fun posicao(
        quantidade: String,
        dataPosicao: LocalDate,
    ): PosicaoEstoque =
        PosicaoEstoque(
            codigoIbgeMunicipio = ibge,
            cnes = cnes,
            nomeUnidade = "UBS TESTE RESPOSTA ANTIGA",
            logradouro = "RUA A",
            numero = "1",
            bairro = "CENTRO",
            cep = "57480000",
            telefone = null,
            latitude = -9.387756,
            longitude = -38.000369,
            catmat = "BR0267691U0042",
            descricaoProduto = "METFORMINA, CLORIDRATO 850 MG COMPRIMIDO",
            quantidade = BigDecimal(quantidade),
            numeroLote = "L1",
            validade = LocalDate.of(2027, 10, 31),
            dataPosicao = dataPosicao,
        )

    @Test
    fun `resposta antiga nao regride estoque nem gera novo aviso`() {
        val usuario = usuarios.saveAndFlush(
            Usuario(
                nome = "Cidadão Teste",
                email = "dados-antigos-avisos@teste.com",
                senhaHash = "hash-teste",
                papel = PapelUsuario.CIDADAO,
            ),
        )

        val ubs = unidades.saveAndFlush(
            Ubs(
                cnes = cnes,
                nome = "UBS Teste Resposta Antiga",
                tipo = TipoUnidade.UBS,
                bairro = "Centro",
                endereco = "Rua A, 1",
                codigoIbgeMunicipio = ibge,
                latitude = -9.387756,
                longitude = -38.000369,
            ),
        )

        val medicamento = medicamentos.saveAndFlush(
            Medicamento(
                catmat = "BR0267691",
                nome = "Metformina",
                concentracao = "850 mg",
                formaFarmaceutica = "comprimido",
                categoria = "Diabetes",
                rename = false,
            ),
        )

        listaDesejos.saveAndFlush(
            ItemListaDesejo(
                usuario = usuario,
                ubs = ubs,
                medicamento = medicamento,
                notificar = true,
            ),
        )

        val usuarioId = requireNotNull(usuario.id)
        val ubsId = requireNotNull(ubs.id)

        // A fonte primeiro retorna dados recentes, depois
        // uma resposta mais antiga, e então os dados recentes
        // novamente.
        val respostas = listOf(
            listOf(posicao("100", dataRecente)),
            listOf(posicao("0", dataAntiga)),
            listOf(posicao("100", dataRecente)),
        )

        var stub = given(fonte.buscarPosicoes(ibge, null))
        respostas.forEach { resposta ->
            stub = stub.willReturn(resposta)
        }

        fun verificarEstadoEsperado() {
            em.flush()
            em.clear()

            val item = itens.findByUbs(ubsId).single()

            assertEquals(StatusEstoque.DISPONIVEL, item.status)
            assertEquals(100, item.quantidade)

            val dataEsperada = dataRecente
                .atStartOfDay(ZoneId.of("America/Maceio"))
                .toInstant()

            assertEquals(dataEsperada, item.atualizadoEm)

            val unidade = unidades.findByCnesIn(listOf(cnes)).single()
            assertEquals(
                dataEsperada,
                unidade.ultimaSincronizacao,
            )

            assertEquals(
                1,
                avisos.findAllByUsuarioIdOrderByCriadoEmDescIdDesc(
                    usuarioId,
                ).size,
                "Uma resposta antiga não deve causar avisos duplicados",
            )
        }

        // Primeira execução: medicamento disponível.
        sincronizacao.sincronizar(ibge)
        verificarEstadoEsperado()

        // Segunda execução: resposta de um dia anterior.
        // Não deve substituir o estoque mais recente.
        sincronizacao.sincronizar(ibge)
        verificarEstadoEsperado()

        // Terceira execução: os mesmos dados recentes.
        // Não deve criar outra transição nem outro aviso.
        sincronizacao.sincronizar(ibge)
        verificarEstadoEsperado()
    }
}
