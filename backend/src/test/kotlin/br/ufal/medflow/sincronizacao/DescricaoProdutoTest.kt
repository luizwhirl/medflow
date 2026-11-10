package br.ufal.medflow.sincronizacao

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** As descrições são reais, tiradas da BNAFAR (Delmiro Gouveia e São Miguel dos Campos). */
class DescricaoProdutoTest {

    private fun esperar(descricao: String, nome: String, concentracao: String, forma: String) {
        assertEquals(ProdutoDescrito(nome, concentracao, forma), DescricaoProduto.analisar(descricao), descricao)
    }

    @Test
    fun `separa nome, concentracao e forma`() {
        esperar("METFORMINA, CLORIDRATO 850 MG COMPRIMIDO", "Metformina, cloridrato", "850 mg", "comprimido")
        esperar("ACEBROFILINA 10 MG/ML XAROPE  100 ML", "Acebrofilina", "10 mg/ml", "xarope 100 ml")
        esperar("CLONAZEPAM 0,5 MG COMPRIMIDO ELENCO ESTADUAL", "Clonazepam", "0,5 mg", "comprimido")
        esperar("AMOXICILINA 50 MG/ML PÓ PARA SUSPENSÃO 150 ML", "Amoxicilina", "50 mg/ml", "pó para suspensão 150 ml")
        esperar("CLORETO DE SÓDIO 0,9% SOLUÇÃO 100 ML NÃO INJETÁVEL", "Cloreto de sódio", "0,9%", "solução 100 ml não injetável")
    }

    @Test
    fun `entende dose composta`() {
        esperar(
            "CARBONATO DE CÁLCIO + COLECALCIFEROL  500 MG + 400 UI COMPRIMIDO",
            "Carbonato de cálcio + colecalciferol", "500 mg + 400 UI", "comprimido",
        )
        esperar("ISONIAZIDA + RIFAMPICINA 75 + 150 MG COMPRIMIDO", "Isoniazida + rifampicina", "75 + 150 mg", "comprimido")
    }

    @Test
    fun `separa a dose colada na unidade e mantem siglas`() {
        esperar(
            "HIDROCORTISONA, SUCCINATO SÓDICO 500MG PÓ PARA SOLUÇÃO INJETÁVEL",
            "Hidrocortisona, succinato sódico", "500 mg", "pó para solução injetável",
        )
        esperar(
            "INSULINA HUMANA NPH 100 UI/ML SUSPENSÃO INJETÁVEL 10 ML",
            "Insulina humana NPH", "100 UI/ml", "suspensão injetável 10 ml",
        )
    }

    @Test
    fun `expande abreviacoes da forma`() {
        esperar("CLOPIDOGREL 75 MG COMP GRUPO 2", "Clopidogrel", "75 mg", "comprimido")
        esperar("DEXAMETASONA  4 MG/ML SOL INJ (AMP) 2,5ML", "Dexametasona", "4 mg/ml", "solução injetável (ampola) 2,5ml")
        esperar("METRONIDAZOL 100 MG/G BISNAGA 50 G", "Metronidazol", "100 mg/g", "bisnaga 50 g")
    }

    @Test
    fun `material hospitalar nao e medicamento`() {
        listOf(
            "ATADURA, CREPOM, 100% ALGODÃO, 10 CM, 180 CM, EM REPOUSO, 13 FIOS UN/CM², EMBALAGEM INDIVIDUAL",
            "AGULHA DESCARTÁVEL, AÇO INOXIDÁVEL, 4 MM, 32 G, CANETA APLICADORA DE INSULINA, ESTÉRIL, EMBALAGEM INDIVIDUAL",
            "ESPAÇADOR, COM MÁSCARA ADULTO ULTRA-FLEXÍVEL BIVALVULADA, ADAPTADOR UNIVERSAL PARA SPRAY AEROSSOL, TRANSPARENTE",
            "FRASCO COLETOR PARA EXAME, PLÁSTICO, DESCARTÁVEL, 50 A 80 ML, ESTÉRIL, COLETA DE URINA",
            "LUVA CIRÚRGICA, LÁTEX NATURAL, 7,50, ESTÉRIL, COMPRIMENTO MÍNIMO DE 28CM, LUBRIFICADA C/ PÓ BIOABSORVÍVEL",
            "MONITOR DE GLICEMIA ATÉ 600 MG/DL, ATÉ 10 S, ATÉ 250 TESTES",
            "GEL CONDUTOR PARA ULTRASSONOGRAFIA 1 KG",
            "COMPRESSA GAZE, 100% ALGODÃO, 13 FIOS, 8 CAMADAS, 7,50 X 7,50 CM, 5 DOBRAS, ESTÉRIL",
        ).forEach { assertNull(DescricaoProduto.analisar(it), it) }
    }

    @Test
    fun `descricao vazia ou nula nao e medicamento`() {
        assertNull(DescricaoProduto.analisar(null))
        assertNull(DescricaoProduto.analisar("   "))
    }
}
