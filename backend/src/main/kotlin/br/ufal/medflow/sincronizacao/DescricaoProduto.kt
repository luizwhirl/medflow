package br.ufal.medflow.sincronizacao

import java.text.Normalizer

/** Medicamento extraído do texto livre `descricao_produto` da BNAFAR. */
data class ProdutoDescrito(val nome: String, val concentracao: String, val forma: String)

/**
 * A BNAFAR só traz uma descrição como "METFORMINA, CLORIDRATO 850 MG COMPRIMIDO".
 * Aqui ela vira nome, concentração e forma. A base mistura medicamentos com materiais
 * (agulhas, curativos...), então só vale como medicamento o que tem dose e forma farmacêutica.
 */
object DescricaoProduto {

    private const val UNIDADE = "(?:(?:MG|MCG|G|UI|MEQ|ML)(?!\\p{L})|%)"

    /** Dose simples (850 MG), por volume (10 MG/ML) ou composta (500 MG + 400 UI). */
    private val DOSE = Regex(
        "(?:\\d[\\d.,]*(?:\\s*$UNIDADE)?\\s*\\+\\s*)*\\d[\\d.,]*\\s*$UNIDADE(?:\\s*/\\s*\\d*\\s*(?:ML|G|MG|L|DOSES?)(?!\\p{L}))?",
        RegexOption.IGNORE_CASE,
    )

    private val FORMAS = setOf(
        "COMPRIMIDO", "COMP", "CAPSULA", "CAP", "XAROPE", "SOLUCAO", "SOL", "SUSPENSAO", "SUSP", "POMADA", "CREME",
        "GEL", "AEROSSOL", "SPRAY", "XAMPU", "INJETAVEL", "INJ", "AMP", "BISNAGA", "GOTAS", "SUPOSITORIO", "OVULO",
        "COLIRIO", "LOCAO", "EMULSAO", "PASTILHA", "DRAGEA", "ELIXIR", "PO",
    )

    private val ABREVIACOES = mapOf(
        "comp" to "comprimido", "cap" to "cápsula", "amp" to "ampola", "susp" to "suspensão", "inj" to "injetável",
        "sol" to "solução",
    )

    private val RUIDO_DA_FORMA = Regex("ELENCO ESTADUAL|GRUPO \\d+", RegexOption.IGNORE_CASE)

    fun analisar(descricao: String?): ProdutoDescrito? {
        val texto = descricao?.replace(Regex("\\s+"), " ")?.trim(' ', '-') ?: return null
        val dose = DOSE.find(texto) ?: return null

        val resto = texto.substring(dose.range.last + 1)
        val palavras = Regex("[A-Z]+").findAll(semAcento(resto)).map { it.value }.toSet()
        if (palavras.none { it in FORMAS }) return null

        val nome = texto.substring(0, dose.range.first).trim(' ', ',')
        if (nome.isEmpty()) return null

        return ProdutoDescrito(
            nome = nome.lowercase().replaceFirstChar { it.uppercase() }.replace(Regex("\\bnph\\b"), "NPH"),
            concentracao = formatarConcentracao(dose.value),
            forma = formatarForma(resto),
        )
    }

    private fun formatarConcentracao(dose: String): String =
        dose.replace(Regex("\\s+"), " ").lowercase()
            .replace(Regex("(\\d)\\s*(mg|mcg|meq|ml|ui|g)(?![a-z])"), "$1 $2")
            .replace(Regex("\\s*%"), "%")
            .replace(Regex("\\bui\\b"), "UI")

    private fun formatarForma(resto: String): String =
        resto.replace(RUIDO_DA_FORMA, "").replace(Regex("\\s+"), " ").trim(' ', ',').lowercase()
            .replace(Regex("[a-zà-ÿ]+")) { ABREVIACOES[it.value] ?: it.value }

    private fun semAcento(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").uppercase()
}
