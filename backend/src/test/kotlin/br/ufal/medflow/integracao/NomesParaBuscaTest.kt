package br.ufal.medflow.integracao

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NomesParaBuscaTest {

    @Test
    fun `tira o tipo de unidade e o distrito sanitario do nome`() {
        assertEquals("DURVAL CORTEZ", NomesParaBusca.unidade("UNIDADE DE SAUDE DA FAMILIA DURVAL CORTEZ 2 DS"))
        assertEquals("ALIOMAR ALMEIDA LINS", NomesParaBusca.unidade("UNIDADE DE SAUDE ALIOMAR ALMEIDA LINS 6 DS"))
        assertEquals("MARIA TEREZA HOLANDA COSTA", NomesParaBusca.unidade("UNID BASICA DE SAUDE MARIA TEREZA HOLANDA COSTA 3 DS"))
        assertEquals("DR IB GATTO FALCAO", NomesParaBusca.unidade("URS DR IB GATTO FALCAO 7 DS"))
    }

    @Test
    fun `nome que vira so o tipo nao serve para a busca`() {
        assertNull(NomesParaBusca.unidade("UBS 3 DS"))
    }

    @Test
    fun `abre as abreviacoes de bairro da BNAFAR`() {
        assertEquals("BENEDITO BENTES II", NomesParaBusca.bairro("B BENTES II"))
        assertEquals("CIDADE UNIVERSITARIA", NomesParaBusca.bairro("CID UNIVERSITARIA"))
        assertEquals("CHA DA JAQUEIRA", NomesParaBusca.bairro("Chã da Jaqueira"))
    }

    @Test
    fun `bairro digitado errado acha o parecido de outra unidade`() {
        val daFonte = listOf("OURO PRETO", "Ouro Preto", "OURO PREO", "JACINTINHO", "CENTRO")
        assertEquals("OURO PRETO", NomesParaBusca.parecido("OURO PREO", daFonte))
        assertNull(NomesParaBusca.parecido("JACINTINHO", daFonte)) // só ele mesmo
        assertNull(NomesParaBusca.parecido("CENTRAL", daFonte)) // nome curto não conta
        assertNull(NomesParaBusca.parecido("PONTA VERDE", daFonte))
    }

    @Test
    fun `bairro vazio ou nao informado nao e procurado`() {
        assertNull(NomesParaBusca.bairro(null))
        assertNull(NomesParaBusca.bairro("Não informado"))
    }
}
