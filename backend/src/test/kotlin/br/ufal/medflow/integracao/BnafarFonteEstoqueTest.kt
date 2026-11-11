package br.ufal.medflow.integracao

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Usa respostas reais da BNAFAR gravadas em `src/test/resources/bnafar` (Delmiro Gouveia, 3 lotes).
 * Nenhum teste chama a API de verdade.
 */
class BnafarFonteEstoqueTest {

    private val base = "http://bnafar.test/daf/estoque-medicamentos-bnafar-horus"

    private fun fonte(props: BnafarProperties, servidor: (MockRestServiceServer) -> Unit): BnafarFonteEstoque {
        val builder = RestClient.builder().baseUrl(props.baseUrl)
        servidor(MockRestServiceServer.bindTo(builder).build())
        return BnafarFonteEstoque(builder.build(), props)
    }

    private fun props(tamanhoPagina: Int = 2, maxPaginas: Int = 10) =
        BnafarProperties(baseUrl = "http://bnafar.test", tamanhoPagina = tamanhoPagina, maxPaginas = maxPaginas)

    private fun fixture(nome: String): String = javaClass.getResource("/bnafar/$nome")!!.readText()

    private fun MockRestServiceServer.pagina(url: String, corpo: String) {
        expect(requestTo(url)).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `pagina cheia leva a proxima e a pagina incompleta encerra`() {
        lateinit var servidor: MockRestServiceServer
        val fonte = fonte(props()) {
            servidor = it
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", fixture("pagina-cheia.json"))
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=1", fixture("pagina-final.json"))
        }

        val posicoes = fonte.buscarPosicoes("2702405")

        servidor.verify()
        assertEquals(3, posicoes.size)
        assertEquals(listOf("BR0267691U0042", "BR0267663U0042", "BR0267650U0042"), posicoes.map { it.catmat })
        assertEquals(0, BigDecimal("410").compareTo(posicoes[2].quantidade))
    }

    @Test
    fun `mapeia os campos do registro real`() {
        val fonte = fonte(props()) {
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", fixture("pagina-final.json"))
        }

        val p = fonte.buscarPosicoes("270240").single()

        assertEquals("2702405", p.codigoIbgeMunicipio)
        assertEquals("9674217", p.cnes)
        assertEquals("UBS MARIA APARECIDA LISBOA", p.nomeUnidade)
        assertEquals("RUA ADAUTO SILVA", p.logradouro)
        assertEquals("S/N", p.numero)
        assertEquals("CENTRO", p.bairro)
        assertEquals("57480000", p.cep)
        assertNull(p.telefone)
        assertEquals(-9.387756, p.latitude)
        assertEquals(-38.000369, p.longitude)
        assertEquals("BR0267650U0042", p.catmat)
        assertEquals("ENALAPRIL, MALEATO 5 MG COMPRIMIDO", p.descricaoProduto)
        assertEquals(0, BigDecimal("410").compareTo(p.quantidade))
        assertEquals("125064", p.numeroLote)
        assertEquals(LocalDate.of(2027, 12, 2), p.validade)
        assertEquals(LocalDate.of(2026, 11, 3), p.dataPosicao)
    }

    @Test
    fun `pagina cheia seguida de vazia nao perde registros`() {
        lateinit var servidor: MockRestServiceServer
        val fonte = fonte(props()) {
            servidor = it
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", fixture("pagina-cheia.json"))
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=1", fixture("pagina-vazia.json"))
        }

        assertEquals(2, fonte.buscarPosicoes("270240").size)
        servidor.verify()
    }

    @Test
    fun `envia a data da posicao quando informada`() {
        val fonte = fonte(props()) {
            it.pagina(
                "$base?codigo_municipio=270240&data_posicao_estoque=2026-11-03&limit=2&offset=0",
                fixture("pagina-vazia.json"),
            )
        }

        assertEquals(emptyList(), fonte.buscarPosicoes("270240", LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun `erro HTTP vira excecao da fonte`() {
        val fonte = fonte(props()) {
            it.expect(requestTo("$base?codigo_municipio=270240&limit=2&offset=0")).andRespond(withServerError())
        }

        assertFailsWith<FonteEstoqueIndisponivelException> { fonte.buscarPosicoes("270240") }
    }

    @Test
    fun `falha de conexao ou timeout vira excecao da fonte`() {
        val fonte = fonte(props()) {
            it.expect(requestTo("$base?codigo_municipio=270240&limit=2&offset=0"))
                .andRespond(withException(IOException("Read timed out")))
        }

        assertFailsWith<FonteEstoqueIndisponivelException> { fonte.buscarPosicoes("270240") }
    }

    @Test
    fun `corpo que nao e JSON vira excecao da fonte`() {
        val fonte = fonte(props()) {
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", "<html>manutencao</html>")
        }

        assertFailsWith<FonteEstoqueIndisponivelException> { fonte.buscarPosicoes("270240") }
    }

    @Test
    fun `registro sem CATMAT e descartado e os demais seguem`() {
        val semCatmat = fixture("pagina-cheia.json")
            .replace("\"codigo_catmat\": \"BR0267691U0042\"", "\"codigo_catmat\": null")
        val fonte = fonte(props()) {
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", semCatmat)
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=1", fixture("pagina-vazia.json"))
        }

        val posicoes = fonte.buscarPosicoes("270240")

        assertEquals(listOf("BR0267663U0042"), posicoes.map { it.catmat })
    }

    @Test
    fun `registro sem quantidade e descartado`() {
        val semQuantidade = fixture("pagina-cheia.json")
            .replace("\"quantidade_estoque\": 0.0", "\"quantidade_estoque\": null")
        val fonte = fonte(props()) {
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", semQuantidade)
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=1", fixture("pagina-vazia.json"))
        }

        assertEquals(emptyList(), fonte.buscarPosicoes("270240"))
    }

    @Test
    fun `registro sem data da posicao e descartado`() {
        val semData = fixture("pagina-cheia.json")
            .replace("\"data_posicao_estoque\": \"2026-11-03\"", "\"data_posicao_estoque\": null")
        val fonte = fonte(props()) {
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", semData)
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=1", fixture("pagina-vazia.json"))
        }

        assertEquals(emptyList(), fonte.buscarPosicoes("270240"))
    }

    @Test
    fun `validade ausente ou ilegivel vira nula sem descartar o registro`() {
        val semValidade = fixture("pagina-cheia.json")
            .replace("\"data_validade\": \"2027-10-31 00:00:00-03\"", "\"data_validade\": null")
            .replace("\"data_validade\": \"2028-05-01 00:00:00-03\"", "\"data_validade\": \"sem data\"")
        val fonte = fonte(props()) {
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", semValidade)
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=1", fixture("pagina-vazia.json"))
        }

        val posicoes = fonte.buscarPosicoes("270240")

        assertEquals(2, posicoes.size)
        assertNull(posicoes[0].validade)
        assertNull(posicoes[1].validade)
    }

    @Test
    fun `CNES sem zeros a esquerda e completado para 7 digitos`() {
        val cnesCurto = fixture("pagina-final.json").replace("\"codigo_cnes\": 9674217", "\"codigo_cnes\": 674217")
        val fonte = fonte(props()) {
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", cnesCurto)
        }

        assertEquals("0674217", fonte.buscarPosicoes("270240").single().cnes)
    }

    @Test
    fun `municipio fora da regra do digito verificador sai com o codigo oficial`() {
        val quixaba = fixture("pagina-final.json").replace("\"codigo_municipio\": 270240", "\"codigo_municipio\": 261153")
        val fonte = fonte(props()) {
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", quixaba)
        }

        assertEquals("2611533", fonte.buscarPosicoes("270240").single().codigoIbgeMunicipio)
    }

    @Test
    fun `registro com municipio de 5 digitos e descartado sem derrubar a busca`() {
        val curto = fixture("pagina-cheia.json")
            .replaceFirst("\"codigo_municipio\": 270240", "\"codigo_municipio\": 27024")
        val fonte = fonte(props()) {
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", curto)
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=1", fixture("pagina-vazia.json"))
        }

        assertEquals(listOf("BR0267663U0042"), fonte.buscarPosicoes("270240").map { it.catmat })
    }

    @Test
    fun `registro com municipio de 8 digitos e descartado sem derrubar a busca`() {
        val longo = fixture("pagina-cheia.json")
            .replaceFirst("\"codigo_municipio\": 270240", "\"codigo_municipio\": 27024000")
        val fonte = fonte(props()) {
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", longo)
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=1", fixture("pagina-vazia.json"))
        }

        assertEquals(listOf("BR0267663U0042"), fonte.buscarPosicoes("270240").map { it.catmat })
    }

    /** Mesma página real, mas com outros lotes, para parecer uma segunda página diferente. */
    private fun outraPaginaCheia() = fixture("pagina-cheia.json")
        .replace("\"numero_lote\": \"25104312\"", "\"numero_lote\": \"OUTRO-1\"")
        .replace("\"numero_lote\": \"25E19D\"", "\"numero_lote\": \"OUTRO-2\"")

    @Test
    fun `le todas as paginas, pedindo offset como numero da pagina`() {
        lateinit var servidor: MockRestServiceServer
        val fonte = fonte(props()) {
            servidor = it
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", fixture("pagina-cheia.json"))
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=1", outraPaginaCheia())
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=2", fixture("pagina-final.json"))
        }

        val posicoes = fonte.buscarPosicoes("270240")

        servidor.verify()
        assertEquals(5, posicoes.size)
        assertEquals(listOf("25104312", "25E19D", "OUTRO-1", "OUTRO-2", "125064"), posicoes.map { it.numeroLote })
    }

    @Test
    fun `pagina que repete a anterior falha em vez de somar duas vezes`() {
        val fonte = fonte(props()) {
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", fixture("pagina-cheia.json"))
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=1", fixture("pagina-cheia.json"))
        }

        val erro = assertFailsWith<FonteEstoqueIndisponivelException> { fonte.buscarPosicoes("270240") }

        assertTrue("repetiu registros" in erro.message.orEmpty(), erro.message)
    }

    @Test
    fun `falha em vez de truncar quando passa do limite de paginas`() {
        val fonte = fonte(props(maxPaginas = 2)) {
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=0", fixture("pagina-cheia.json"))
            it.pagina("$base?codigo_municipio=270240&limit=2&offset=1", outraPaginaCheia())
        }

        val erro = assertFailsWith<FonteEstoqueIndisponivelException> { fonte.buscarPosicoes("270240") }

        assertTrue("limite de 2 páginas" in erro.message.orEmpty(), erro.message)
    }

    @Test
    fun `rejeita codigo IBGE invalido sem chamar a API`() {
        val fonte = fonte(props()) { }

        assertFailsWith<IllegalArgumentException> { fonte.buscarPosicoes("27") }
    }
}
