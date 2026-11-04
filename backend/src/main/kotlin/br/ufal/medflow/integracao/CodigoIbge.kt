package br.ufal.medflow.integracao

/** O IBGE tem 7 dígitos; a BNAFAR usa os 6 primeiros (sem o dígito verificador). */
object CodigoIbge {

    private val PESOS = intArrayOf(1, 2, 1, 2, 1, 2)

    fun paraSeteDigitos(codigo: String): String {
        val limpo = validar(codigo)
        return if (limpo.length == 7) limpo else limpo + digitoVerificador(limpo)
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
