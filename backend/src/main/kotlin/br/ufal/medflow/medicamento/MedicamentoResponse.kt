package br.ufal.medflow.medicamento

data class MedicamentoResponse(
    val id: Long,
    val catmat: String,
    val nome: String,
    val concentracao: String,
    val formaFarmaceutica: String,
    val categoria: String,
    val rename: Boolean,
)

fun Medicamento.toResponse() = MedicamentoResponse(
    id = requireNotNull(id),
    catmat = catmat,
    nome = nome,
    concentracao = concentracao,
    formaFarmaceutica = formaFarmaceutica,
    categoria = categoria,
    rename = rename,
)
