package br.ufal.medflow.sincronizacao

object Catmat {

    private val COM_UNIDADE = Regex("^(BR\\d{7})U\\d+[A-Z]*$")

    /**
     * A BNAFAR manda o CATMAT com a unidade de fornecimento (BR0267691U0042) e, em itens inativados,
     * com um sufixo a mais (BR0266863U0066INAT). O medicamento é só BR + 7 dígitos.
     */
    fun base(catmat: String): String {
        val limpo = catmat.trim().uppercase()
        return COM_UNIDADE.matchEntire(limpo)?.groupValues?.get(1) ?: limpo
    }
}
