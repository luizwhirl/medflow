package br.ufal.medflow.sincronizacao

import br.ufal.medflow.ubs.TipoUnidade
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Nomes reais de unidades da BNAFAR (Delmiro Gouveia, São Miguel dos Campos e Cajueiro). */
class ClassificacaoUnidadeTest {

    @Test
    fun `unidades basicas e farmacias atendem o cidadao`() {
        listOf(
            "UNIDADE BASICA DE SAUDE DOUTOR ULYSSES LUNA",
            "UBS MARIA APARECIDA LISBOA",
            "CENTRO DE SAUDE OLIMPIA LINS LAMENHA",
            "FARMACIA DO POVO",
            "UNIDADE DE SAUDE JOAO PAULO II 5 DS",
        ).forEach { assertEquals(TipoUnidade.UBS, ClassificacaoUnidade.tipo(it), it) }
    }

    @Test
    fun `saude da familia e USF`() {
        listOf(
            "ESF EURIDICE MIRANDA MOREIRA",
            "UNIDADE DE SAUDE DA FAMILIA NOVO MUNDO 5 DS",
            "POSTO MED MARIO FERRO DE MOURA PSF",
            "USF VILLAGE CAMPESTRE II",
        ).forEach { assertEquals(TipoUnidade.USF, ClassificacaoUnidade.tipo(it), it) }
    }

    @Test
    fun `secretaria, almoxarifado, CAPS e UPA nao entram`() {
        listOf(
            "SECRETARIA MUNICIPAL DE SAUDE DE DELMIRO GOUVEIA",
            "CENTRAL DE ABASTECIMENTO FARMACEUTICO CAF",
            "CENTRO ESPECIALIZADO DE ASSISTENCIA FARMACEUTICA",
            "CENTRO DE APOIO PSICOSSOCIAL JOSE DE AQUINO CAPS",
            "UPA DR ULISSES LUNA",
            "HOSPITAL MUNICIPAL DE DELMIRO GOUVEIA",
        ).forEach { assertNull(ClassificacaoUnidade.tipo(it), it) }
    }

    @Test
    fun `nome ausente ou desconhecido nao entra`() {
        assertNull(ClassificacaoUnidade.tipo(null))
        assertNull(ClassificacaoUnidade.tipo("ASSOCIACAO COMUNITARIA"))
    }
}
