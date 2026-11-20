package br.ufal.medflow.aviso

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
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
import org.junit.jupiter.api.BeforeEach
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Verifica a geração de avisos durante a sincronização real com PostgreSQL. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AvisosSincronizacaoIntegrationTests(
    @Autowired private val sincronizacao: SincronizacaoEstoqueService,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val unidades: UbsRepository,
    @Autowired private val medicamentos: MedicamentoRepository,
    @Autowired private val listaDesejos: ItemListaDesejoRepository,
    @Autowired private val avisos: AvisoRepository,
    @Autowired private val em: EntityManager,
) {

    @MockitoBean
    private lateinit var fonte: FonteEstoque

    private val ibge = "2702405"
    private val dia = LocalDate.of(2026, 11, 3)

    private lateinit var ubs: Ubs
    private lateinit var medicamento: Medicamento

    @BeforeEach
    fun preparar() {
        ubs = unidades.saveAndFlush(
            Ubs(
                cnes = "9674217",
                nome = "UBS Maria Aparecida Lisboa",
                tipo = TipoUnidade.UBS,
                bairro = "Centro",
                endereco = "Rua Adauto Silva, S/N",
                codigoIbgeMunicipio = ibge,
                latitude = -9.387756,
                longitude = -38.000369,
            ),
        )

        medicamento = medicamentos.saveAndFlush(
            Medicamento(
                catmat = "BR0267691",
                nome = "Metformina",
                concentracao = "850 mg",
                formaFarmaceutica = "comprimido",
                categoria = "Diabetes",
                rename = false,
            ),
        )
    }

    /** Cria uma posição de estoque semelhante às recebidas da BNAFAR. */
    private fun pos(
        quantidade: String,
        dataPosicao: LocalDate = dia,
    ): PosicaoEstoque =
        PosicaoEstoque(
            codigoIbgeMunicipio = ibge,
            cnes = "9674217",
            nomeUnidade = "UBS MARIA APARECIDA LISBOA",
            logradouro = "RUA ADAUTO SILVA",
            numero = "S/N",
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

    /** Define os saldos devolvidos em cada execução da sincronização. */
    private fun fonteDevolve(vararg quantidades: String) {
        var stub = given(fonte.buscarPosicoes(ibge, null))

        quantidades.forEach { quantidade ->
            stub = stub.willReturn(listOf(pos(quantidade)))
        }
    }

    /** Cria um cidadão com um medicamento na lista de desejos. */
    private fun criarCidadao(
        email: String,
        notificar: Boolean = true,
    ): Usuario {
        val usuario = usuarios.saveAndFlush(
            Usuario(
                nome = "Cidadão Teste",
                email = email,
                senhaHash = "hash-teste",
                papel = PapelUsuario.CIDADAO,
            ),
        )

        listaDesejos.saveAndFlush(
            ItemListaDesejo(
                usuario = usuario,
                ubs = ubs,
                medicamento = medicamento,
                notificar = notificar,
            ),
        )

        return usuario
    }

    /** Consulta avisos persistidos, descartando o cache do JPA. */
    private fun avisosDo(usuario: Usuario): List<Aviso> {
        em.flush()
        em.clear()

        return avisos.findAllByUsuarioIdOrderByCriadoEmDescIdDesc(
            requireNotNull(usuario.id),
        )
    }

    @Test
    fun `gera avisos na primeira disponibilidade do medicamento`() {
        val primeiro = criarCidadao("primeiro-sync@teste.com")
        val segundo = criarCidadao("segundo-sync@teste.com")

        fonteDevolve("100")

        sincronizacao.sincronizar(ibge)

        val avisosPrimeiro = avisosDo(primeiro)
        val avisosSegundo = avisosDo(segundo)

        assertEquals(1, avisosPrimeiro.size)
        assertEquals(1, avisosSegundo.size)

        val aviso = avisosPrimeiro.single()

        assertEquals(
            TipoAviso.MEDICAMENTO_DISPONIVEL,
            aviso.tipo,
        )
        assertEquals("Medicamento disponível", aviso.titulo)
        assertTrue(aviso.texto.contains("Metformina"))
        assertTrue(aviso.texto.contains("UBS Maria Aparecida Lisboa"))
        assertFalse(aviso.lido)

        // Os dois cidadãos foram avisados sobre a mesma transição.
        assertEquals(
            aviso.chaveEvento,
            avisosSegundo.single().chaveEvento,
        )
    }

    @Test
    fun `sincronizacoes repetidas nao duplicam avisos`() {
        val cidadao = criarCidadao("repeticao-sync@teste.com")

        // A quantidade muda, mas o medicamento permanece disponível.
        fonteDevolve("100", "150", "150")

        sincronizacao.sincronizar(ibge)
        assertEquals(1, avisosDo(cidadao).size)

        sincronizacao.sincronizar(ibge)
        assertEquals(1, avisosDo(cidadao).size)

        sincronizacao.sincronizar(ibge)
        assertEquals(1, avisosDo(cidadao).size)
    }

    @Test
    fun `gera novo aviso quando medicamento volta a ficar disponivel`() {
        val cidadao = criarCidadao("reposicao-sync@teste.com")

        // Sem estoque, reposição, falta novamente e nova reposição.
        fonteDevolve("0", "100", "0", "100")

        sincronizacao.sincronizar(ibge)
        assertTrue(avisosDo(cidadao).isEmpty())

        sincronizacao.sincronizar(ibge)
        val primeiraDisponibilidade = avisosDo(cidadao)

        assertEquals(1, primeiraDisponibilidade.size)

        sincronizacao.sincronizar(ibge)
        assertEquals(1, avisosDo(cidadao).size)

        sincronizacao.sincronizar(ibge)
        val aposNovaReposicao = avisosDo(cidadao)

        assertEquals(2, aposNovaReposicao.size)

        // Uma nova transição deve ter uma chave de evento diferente.
        assertNotEquals(
            aposNovaReposicao[0].chaveEvento,
            aposNovaReposicao[1].chaveEvento,
        )

        assertTrue(
            aposNovaReposicao.all {
                it.tipo == TipoAviso.MEDICAMENTO_DISPONIVEL
            },
        )
    }

    @Test
    fun `nao avisa cidadao que desativou notificacoes`() {
        val interessado = criarCidadao(
            "interessado-sync@teste.com",
            notificar = true,
        )

        val desativado = criarCidadao(
            "desativado-sync@teste.com",
            notificar = false,
        )

        fonteDevolve("100")

        sincronizacao.sincronizar(ibge)

        assertEquals(1, avisosDo(interessado).size)
        assertTrue(avisosDo(desativado).isEmpty())
    }

    @Test
    fun `gera aviso quando estoque reservado passa a disponivel`() {
        val cidadao = criarCidadao("reservado-sync@teste.com")

        // Pela regra de estoque existente, 5 unidades ficam RESERVADAS.
        fonteDevolve("5", "100")

        sincronizacao.sincronizar(ibge)

        // Ainda não existe disponibilidade efetiva.
        assertTrue(avisosDo(cidadao).isEmpty())

        sincronizacao.sincronizar(ibge)

        assertEquals(1, avisosDo(cidadao).size)
    }
}
