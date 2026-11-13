package br.ufal.medflow.sincronizacao

data class Contagem(val criados: Int = 0, val atualizados: Int = 0, val removidos: Int = 0) {
    val houveMudanca get() = criados + atualizados + removidos > 0
}

/** Registros da fonte que não entraram: não são pontos de atendimento, sem coordenadas ou não são medicamento. */
data class Descartes(val naoUbs: Int = 0, val semCoordenadas: Int = 0, val naoMedicamento: Int = 0)

data class ResultadoSincronizacao(
    val codigoIbge: String,
    val posicoesLidas: Int,
    val descartes: Descartes,
    val unidades: Contagem,
    val medicamentos: Contagem,
    val itens: Contagem,
    val lotes: Contagem,
) {
    val houveMudanca get() = listOf(unidades, medicamentos, itens, lotes).any { it.houveMudanca }
}
