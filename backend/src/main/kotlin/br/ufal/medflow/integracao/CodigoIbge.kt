package br.ufal.medflow.integracao

/** O IBGE tem 7 dígitos; a BNAFAR usa os 6 primeiros (sem o dígito verificador). */
object CodigoIbge {

    private val PESOS = intArrayOf(1, 2, 1, 2, 1, 2)

    /**
     * Municípios cujo dígito verificador não segue a regra (6 dígitos -> 7 dígitos).
     * Conferido contra a lista de municípios do IBGE em 05/11/2026 (5571 municípios, só estes 9 fogem).
     */
    private val EXCECOES = mapOf(
        "220191" to "2201919",
        "220198" to "2201988",
        "220225" to "2202251",
        "261153" to "2611533",
        "311783" to "3117836",
        "315213" to "3152131",
        "430587" to "4305871",
        "520393" to "5203939",
        "520396" to "5203962",
    )

    fun paraSeteDigitos(codigo: String): String {
        val limpo = validar(codigo)
        if (limpo.length == 7) return limpo
        return EXCECOES[limpo] ?: (limpo + digitoVerificador(limpo))
    }

    fun paraSeisDigitos(codigo: String): String = validar(codigo).take(6)

    private fun validar(codigo: String): String {
        val limpo = codigo.trim()
        require(limpo.length in 6..7 && limpo.all { it.isDigit() }) {
            "Código IBGE inválido (esperado 6 ou 7 dígitos): '$codigo'"
        }
        return limpo
    }

    /** Dígito verificador: pesos 1 e 2 alternados, somando os algarismos de cada produto. */
    private fun digitoVerificador(seisDigitos: String): Char {
        val soma = seisDigitos.mapIndexed { i, c ->
            val produto = c.digitToInt() * PESOS[i]
            produto / 10 + produto % 10
        }.sum()
        return ((10 - soma % 10) % 10).digitToChar()
    }
}
