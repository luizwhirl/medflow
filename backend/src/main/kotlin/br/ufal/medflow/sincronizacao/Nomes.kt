package br.ufal.medflow.sincronizacao

/** A BNAFAR manda nomes e endereços em maiúsculas e sem acento. Aqui viram texto legível. */
object Nomes {

    private val CONECTIVOS = setOf("de", "da", "do", "das", "dos", "e")
    private val SIGLAS = setOf("UBS", "USF", "ESF", "PSF", "DS")
    private val ROMANO = Regex("^[IVXLC]{2,}$")

    /** Palavras de todo nome de unidade que perdem o acento na fonte. */
    private val ACENTUADAS = mapOf(
        "basica" to "Básica", "saude" to "Saúde", "farmacia" to "Farmácia", "praca" to "Praça", "sao" to "São",
    )

    fun proprio(texto: String): String =
        texto.trim().split(Regex("\\s+")).mapIndexed { i, palavra ->
            val minuscula = palavra.lowercase()
            when {
                palavra.any { it.isDigit() } || '/' in palavra -> palavra
                palavra in SIGLAS || ROMANO.matches(palavra) -> palavra
                minuscula in ACENTUADAS -> ACENTUADAS.getValue(minuscula)
                i > 0 && minuscula in CONECTIVOS -> minuscula
                else -> minuscula.replaceFirstChar { it.uppercase() }
            }
        }.joinToString(" ")
}
