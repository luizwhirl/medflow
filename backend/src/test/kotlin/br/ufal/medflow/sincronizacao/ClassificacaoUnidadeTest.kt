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

    /** Nomes abreviados de unidades de Maceió na BNAFAR (25/11/2026) que ficavam de fora. */
    @Test
    fun `nomes abreviados da BNAFAR tambem entram`() {
        listOf(
            "UNID BASICA DE SAUDE MARIA TEREZA HOLANDA COSTA 3 DS",
            "US FREI DAMIAO 6 DS",
            "C S ROLAND SIMON PAM VERGEL 2 DS",
            "UNIDADE DE S JOSE TENORIO DE A LINS 5 DS",
            "UNID DE SAUDE DOCENTE ASSIST JOSE LAJES FILHO UNIT 8 DS",
            "UDA DIVALDO SURUAGY 5 DS",
            "UNIDADE DOCENTE ASSISTENCIAL PROF EVA FARIAS",
        ).forEach { assertEquals(TipoUnidade.UBS, ClassificacaoUnidade.tipo(it), it) }
        listOf(
            "US DA FAMILIA SELMA BANDEIRA 6 DS",
            "CLINICA DA FAMILIA VILLAGE CAMPESTRE II",
        ).forEach { assertEquals(TipoUnidade.USF, ClassificacaoUnidade.tipo(it), it) }
    }

    @Test
    fun `telessaude, CAIC e unidades de referencia continuam de fora`() {
        listOf("UNIDADE DE TELESSAUDE CAETES", "CAPS II DR ROSTAND SILVESTRE", "UPA TRAPICHE DA BARRA")
            .forEach { assertNull(ClassificacaoUnidade.tipo(it), it) }
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
