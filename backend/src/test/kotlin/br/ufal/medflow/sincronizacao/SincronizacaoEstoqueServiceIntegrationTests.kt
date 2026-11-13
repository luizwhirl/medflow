package br.ufal.medflow.sincronizacao

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.integracao.FonteEstoque
import br.ufal.medflow.integracao.FonteEstoqueIndisponivelException
import br.ufal.medflow.integracao.PosicaoEstoque
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.TipoUnidade
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Sem o perfil "dev", então o banco começa vazio. Cada teste desfaz o que gravou. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class SincronizacaoEstoqueServiceIntegrationTests(
    @Autowired private val servico: SincronizacaoEstoqueService,
    @Autowired private val medicamentoRepository: MedicamentoRepository,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val em: EntityManager,
) {
    @MockitoBean
    private lateinit var fonte: FonteEstoque

    private val ibge = "2702405"
    private val dia = LocalDate.of(2026, 11, 3)

    /** Padrão: lote de metformina na UBS Maria Aparecida Lisboa (Delmiro Gouveia), como na BNAFAR. */
    private fun pos(
        cnes: String = "9674217",
        unidade: String? = "UBS MARIA APARECIDA LISBOA",
        catmat: String = "BR0267691U0042",
        descricao: String? = "METFORMINA, CLORIDRATO 850 MG COMPRIMIDO",
        quantidade: String = "100",
        lote: String? = "L1",
        validade: LocalDate? = LocalDate.of(2027, 10, 31),
        dataPosicao: LocalDate = dia,
        latitude: Double? = -9.387756,
        longitude: Double? = -38.000369,
    ) = PosicaoEstoque(
        codigoIbgeMunicipio = ibge, cnes = cnes, nomeUnidade = unidade, logradouro = "RUA ADAUTO SILVA", numero = "S/N",
        bairro = "CENTRO", cep = "57480000", telefone = null, latitude = latitude, longitude = longitude,
        catmat = catmat, descricaoProduto = descricao, quantidade = BigDecimal(quantidade), numeroLote = lote,
        validade = validade, dataPosicao = dataPosicao,
    )

    private fun fonteDevolve(vararg rodadas: List<PosicaoEstoque>) {
        var stub = given(fonte.buscarPosicoes(ibge, null))
        rodadas.forEach { stub = stub.willReturn(it) }
    }

    private fun linhas(sql: String): List<String> {
        em.flush()
        return jdbc.queryForList(sql).map { it.toString() }
    }

    private fun estadoDoBanco(): List<String> =
        linhas("select * from ubs order by id") + linhas("select * from medicamento order by id") +
            linhas("select * from item_estoque order by id") + linhas("select * from lote_estoque order by id")

    private fun item(catmat: String) =
        jdbc.queryForMap(
            "select i.quantidade, i.status from item_estoque i join medicamento m on m.id = i.medicamento_id where m.catmat = ?",
            catmat,
        )

    private fun lotes(catmat: String): Map<String, Int> {
        em.flush()
        return jdbc.queryForList(
            """
            select l.numero_lote, l.quantidade from lote_estoque l
            join item_estoque i on i.id = l.item_estoque_id join medicamento m on m.id = i.medicamento_id
            where m.catmat = ?
            """,
            catmat,
        ).associate { it["numero_lote"] as String to it["quantidade"] as Int }
    }

    @Test
    fun `cria unidade, medicamento, item e lotes a partir das posicoes`() {
        fonteDevolve(
            listOf(
                pos(lote = "L1", quantidade = "100", validade = LocalDate.of(2027, 10, 31)),
                pos(lote = "L2", quantidade = "50", validade = LocalDate.of(2028, 1, 15)),
                pos(catmat = "BR0267650U0042", descricao = "ENALAPRIL, MALEATO 5 MG COMPRIMIDO", quantidade = "5", lote = "E1"),
                pos(catmat = "BR0267663U0042", descricao = "FUROSEMIDA 40 MG COMPRIMIDO", quantidade = "0", lote = "F1"),
            ),
        )

        val resultado = servico.sincronizar(ibge)

        assertEquals(Contagem(criados = 1), resultado.unidades)
        assertEquals(Contagem(criados = 3), resultado.medicamentos)
        assertEquals(Contagem(criados = 3), resultado.itens)
        assertEquals(Contagem(criados = 3), resultado.lotes)

        val ubs = jdbc.queryForMap("select * from ubs where cnes = '9674217'")
        assertEquals("UBS Maria Aparecida Lisboa", ubs["nome"])
        assertEquals(TipoUnidade.UBS.name, ubs["tipo"])
        assertEquals("Centro", ubs["bairro"])
        assertEquals("Rua Adauto Silva, S/N", ubs["endereco"])
        assertEquals("2702405", ubs["codigo_ibge_municipio"])
        assertEquals(-9.387756, ubs["latitude"])
        assertEquals(Instant.parse("2026-11-03T03:00:00Z"), (ubs["ultima_sincronizacao"] as java.sql.Timestamp).toInstant())

        val metformina = medicamentoRepository.findByCatmatIn(listOf("BR0267691")).single()
        assertEquals("Metformina, cloridrato", metformina.nome)
        assertEquals("850 mg", metformina.concentracao)
        assertEquals("comprimido", metformina.formaFarmaceutica)
        assertEquals("Diabetes", metformina.categoria)
        assertFalse(metformina.rename)

        assertEquals(150, item("BR0267691")["quantidade"])
        assertEquals(StatusEstoque.DISPONIVEL.name, item("BR0267691")["status"])
        assertEquals(mapOf("L1" to 100, "L2" to 50), lotes("BR0267691"))
        assertEquals(StatusEstoque.RESERVADO.name, item("BR0267650")["status"])
        assertEquals(StatusEstoque.EM_FALTA.name, item("BR0267663")["status"])
        assertEquals(emptyMap(), lotes("BR0267663"))
    }

    @Test
    fun `segunda execucao com os mesmos dados nao muda nada`() {
        val posicoes = listOf(
            pos(lote = "L1", quantidade = "100"),
            pos(lote = "L2", quantidade = "50", validade = LocalDate.of(2028, 1, 15)),
            pos(catmat = "BR0267650U0042", descricao = "ENALAPRIL, MALEATO 5 MG COMPRIMIDO", quantidade = "5", lote = "E1"),
        )
        fonteDevolve(posicoes, posicoes)

        servico.sincronizar(ibge)
        val depoisDaPrimeira = estadoDoBanco()
        em.clear()
        val segunda = servico.sincronizar(ibge)

        assertFalse(segunda.houveMudanca, "segunda execução alterou algo: $segunda")
        assertEquals(depoisDaPrimeira, estadoDoBanco())
        assertTrue(depoisDaPrimeira.isNotEmpty())
    }

    @Test
    fun `mudancas na fonte atualizam item e lotes`() {
        fonteDevolve(
            listOf(
                pos(lote = "L1", quantidade = "100"),
                pos(lote = "L2", quantidade = "50", validade = LocalDate.of(2028, 1, 15)),
            ),
            listOf(
                pos(lote = "L1", quantidade = "80", dataPosicao = dia.plusDays(1)),
                pos(lote = "L3", quantidade = "20", validade = LocalDate.of(2028, 6, 1), dataPosicao = dia.plusDays(1)),
            ),
        )

        servico.sincronizar(ibge)
        em.clear()
        val segunda = servico.sincronizar(ibge)

        assertEquals(Contagem(atualizados = 1), segunda.unidades)
        assertEquals(Contagem(atualizados = 1), segunda.itens)
        assertEquals(Contagem(criados = 1, atualizados = 1, removidos = 1), segunda.lotes)
        assertEquals(100, item("BR0267691")["quantidade"])
        assertEquals(mapOf("L1" to 80, "L3" to 20), lotes("BR0267691"))
    }

    @Test
    fun `lotes repetidos no mesmo item somam e ficam com a menor validade`() {
        fonteDevolve(
            listOf(
                pos(lote = "L1", quantidade = "30", validade = LocalDate.of(2027, 12, 1), catmat = "BR0267691U0042"),
                pos(lote = "L1", quantidade = "20", validade = LocalDate.of(2027, 10, 1), catmat = "BR0267691U0045"),
            ),
        )

        servico.sincronizar(ibge)

        assertEquals(mapOf("L1" to 50), lotes("BR0267691"))
        assertEquals(
            LocalDate.of(2027, 10, 1),
            jdbc.queryForObject("select validade from lote_estoque", java.sql.Date::class.java)!!.toLocalDate(),
        )
        assertEquals(1, medicamentoRepository.findAll().size, "as duas unidades de fornecimento são o mesmo medicamento")
    }

    @Test
    fun `posicao sem validade conta no total mas nao vira lote`() {
        fonteDevolve(listOf(pos(lote = "L1", quantidade = "100"), pos(lote = "L2", quantidade = "40", validade = null)))

        servico.sincronizar(ibge)

        assertEquals(140, item("BR0267691")["quantidade"])
        assertEquals(mapOf("L1" to 100), lotes("BR0267691"))
    }

    @Test
    fun `quantidade fracionada e arredondada para baixo`() {
        fonteDevolve(listOf(pos(quantidade = "10.9")))

        servico.sincronizar(ibge)

        assertEquals(10, item("BR0267691")["quantidade"])
    }

    @Test
    fun `descarta o que nao serve ao cidadao e conta os descartes`() {
        fonteDevolve(
            listOf(
                pos(),
                pos(cnes = "6401457", unidade = "SECRETARIA MUNICIPAL DE SAUDE DE DELMIRO GOUVEIA"),
                pos(cnes = "2010275", unidade = "UNIDADE BASICA DE SAUDE ESTHER SOARES", latitude = null, longitude = null),
                pos(catmat = "BR0270050U0042", descricao = "ATADURA, CREPOM, 100% ALGODÃO, 10 CM, 180 CM, EM REPOUSO"),
            ),
        )

        val resultado = servico.sincronizar(ibge)

        assertEquals(4, resultado.posicoesLidas)
        assertEquals(Descartes(naoUbs = 1, semCoordenadas = 1, naoMedicamento = 1), resultado.descartes)
        assertEquals(listOf("9674217"), jdbc.queryForList("select cnes from ubs", String::class.java))
        assertEquals(1, medicamentoRepository.findAll().size)
    }

    @Test
    fun `medicamento que ja existe nao e sobrescrito`() {
        medicamentoRepository.save(
            Medicamento("BR0267691", "Metformina", "850 mg", "comprimido revestido", "Diabetes", rename = true),
        )
        fonteDevolve(listOf(pos()))

        val resultado = servico.sincronizar(ibge)

        assertEquals(Contagem(), resultado.medicamentos)
        val medicamento = medicamentoRepository.findByCatmatIn(listOf("BR0267691")).single()
        assertEquals("Metformina", medicamento.nome)
        assertEquals("comprimido revestido", medicamento.formaFarmaceutica)
        assertTrue(medicamento.rename)
        assertEquals(100, item("BR0267691")["quantidade"])
    }

    @Test
    fun `atualiza o cadastro da unidade quando a fonte muda`() {
        fonteDevolve(listOf(pos()), listOf(pos(unidade = "UBS MARIA APARECIDA LISBOA II")))

        servico.sincronizar(ibge)
        em.clear()
        val segunda = servico.sincronizar(ibge)

        assertEquals(Contagem(atualizados = 1), segunda.unidades)
        assertEquals("UBS Maria Aparecida Lisboa II", jdbc.queryForObject("select nome from ubs", String::class.java))
    }

    private val enalapril = "ENALAPRIL, MALEATO 5 MG COMPRIMIDO"

    @Test
    fun `item que a unidade deixou de informar vai para zero e em falta e perde os lotes`() {
        val metformina = listOf(
            pos(lote = "L1", quantidade = "100"),
            pos(lote = "L2", quantidade = "50", validade = LocalDate.of(2028, 1, 15)),
        )
        val enalaprilDaUnidade = pos(catmat = "BR0267650U0042", descricao = enalapril, quantidade = "30", lote = "E1")
        fonteDevolve(metformina + enalaprilDaUnidade, listOf(enalaprilDaUnidade), listOf(enalaprilDaUnidade))

        servico.sincronizar(ibge)
        assertEquals(150, item("BR0267691")["quantidade"])
        em.clear()
        val segunda = servico.sincronizar(ibge)

        assertEquals(0, item("BR0267691")["quantidade"])
        assertEquals(StatusEstoque.EM_FALTA.name, item("BR0267691")["status"])
        assertEquals(emptyMap(), lotes("BR0267691"))
        assertEquals(30, item("BR0267650")["quantidade"])
        assertEquals(mapOf("E1" to 30), lotes("BR0267650"))
        assertEquals(Contagem(atualizados = 1), segunda.itens)
        assertEquals(Contagem(removidos = 2), segunda.lotes)

        em.clear()
        val terceira = servico.sincronizar(ibge)
        assertFalse(terceira.houveMudanca, "item já zerado não pode mudar de novo: $terceira")
    }

    @Test
    fun `item zerado volta quando a unidade volta a informar`() {
        fonteDevolve(
            listOf(pos(lote = "L1", quantidade = "100"), pos(catmat = "BR0267650U0042", descricao = enalapril, lote = "E1")),
            listOf(pos(catmat = "BR0267650U0042", descricao = enalapril, lote = "E1")),
            listOf(pos(lote = "L1", quantidade = "70"), pos(catmat = "BR0267650U0042", descricao = enalapril, lote = "E1")),
        )

        servico.sincronizar(ibge)
        em.clear()
        servico.sincronizar(ibge)
        assertEquals(0, item("BR0267691")["quantidade"])
        em.clear()
        servico.sincronizar(ibge)

        assertEquals(70, item("BR0267691")["quantidade"])
        assertEquals(StatusEstoque.DISPONIVEL.name, item("BR0267691")["status"])
        assertEquals(mapOf("L1" to 70), lotes("BR0267691"))
    }

    @Test
    fun `unidade que sumiu inteira da resposta nao e tocada`() {
        val outra = pos(
            cnes = "2004763", unidade = "UNIDADE BASICA DE SAUDE DOUTOR ULYSSES LUNA",
            catmat = "BR0267650U0042", descricao = enalapril, quantidade = "30", lote = "E1",
        )
        fonteDevolve(listOf(pos(lote = "L1", quantidade = "100"), outra), listOf(outra))

        servico.sincronizar(ibge)
        em.clear()
        servico.sincronizar(ibge)

        assertEquals(100, item("BR0267691")["quantidade"])
        assertEquals(StatusEstoque.DISPONIVEL.name, item("BR0267691")["status"])
        assertEquals(mapOf("L1" to 100), lotes("BR0267691"))
    }

    @Test
    fun `so a posicao mais recente de cada item conta`() {
        val ontem = dia.minusDays(1)
        fonteDevolve(
            listOf(
                pos(lote = "L1", quantidade = "100", dataPosicao = ontem),
                pos(lote = "L1", quantidade = "40", dataPosicao = dia),
                pos(lote = "L9", quantidade = "60", dataPosicao = ontem),
            ),
        )

        servico.sincronizar(ibge)

        assertEquals(40, item("BR0267691")["quantidade"])
        assertEquals(mapOf("L1" to 40), lotes("BR0267691"))
    }

    @Test
    fun `fonte indisponivel nao grava nada`() {
        given(fonte.buscarPosicoes(ibge, null)).willThrow(FonteEstoqueIndisponivelException("fora do ar"))

        assertFailsWith<FonteEstoqueIndisponivelException> { servico.sincronizar(ibge) }

        assertTrue(estadoDoBanco().isEmpty())
    }
}
