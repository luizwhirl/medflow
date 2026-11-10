package br.ufal.medflow.sincronizacao

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Nomes, bairros e endereços reais da BNAFAR. */
class NomesTest {

    @Test
    fun `nome de unidade fica legivel e com acento nas palavras comuns`() {
        assertEquals("Unidade Básica de Saúde Doutor Ulysses Luna", Nomes.proprio("UNIDADE BASICA DE SAUDE DOUTOR ULYSSES LUNA"))
        assertEquals("UBS Maria Aparecida Lisboa", Nomes.proprio("UBS MARIA APARECIDA LISBOA"))
        assertEquals("ESF Euridice Miranda Moreira", Nomes.proprio("ESF EURIDICE MIRANDA MOREIRA"))
        assertEquals("Farmácia do Povo", Nomes.proprio("FARMACIA DO POVO"))
        assertEquals("Centro de Saúde Olimpia Lins Lamenha", Nomes.proprio("CENTRO DE SAUDE OLIMPIA LINS LAMENHA"))
    }

    @Test
    fun `mantem siglas, numerais romanos e numeros`() {
        assertEquals("USF Village Campestre II", Nomes.proprio("USF VILLAGE CAMPESTRE II"))
        // só as palavras comuns de unidade ganham acento; nomes próprios ficam como a fonte mandou
        assertEquals("Unidade de Saúde Joao Paulo II 5 DS", Nomes.proprio("UNIDADE DE SAUDE JOAO PAULO II 5 DS"))
        assertEquals("S/N", Nomes.proprio("S/N"))
        assertEquals("Quadra A7", Nomes.proprio("QUADRA A7"))
        assertEquals("Praça Antonieta Texeira Leite", Nomes.proprio("PRACA ANTONIETA TEXEIRA LEITE"))
    }

    @Test
    fun `conectivo no inicio fica com maiuscula`() {
        assertEquals("De Saúde", Nomes.proprio("DE SAUDE"))
    }
}
