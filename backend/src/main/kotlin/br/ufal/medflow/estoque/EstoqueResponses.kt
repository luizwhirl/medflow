package br.ufal.medflow.estoque

import br.ufal.medflow.medicamento.MedicamentoResponse
import br.ufal.medflow.medicamento.toResponse
import br.ufal.medflow.ubs.UbsResponse
import br.ufal.medflow.ubs.toResponse
import java.time.Instant

/** Item do estoque de uma UBS (GET /api/ubs/{id}/estoque). */
data class ItemEstoqueResponse(
    val medicamento: MedicamentoResponse,
    val status: StatusEstoque,
    val quantidade: Int,
    val atualizadoEm: Instant,
)

/** Disponibilidade de um medicamento em uma UBS (GET /api/medicamentos/{id}/disponibilidade). */
data class DisponibilidadeResponse(
    val ubs: UbsResponse,
    val status: StatusEstoque,
    val quantidade: Int,
    val atualizadoEm: Instant,
)

fun ItemEstoque.toItemResponse() = ItemEstoqueResponse(medicamento.toResponse(), status, quantidade, atualizadoEm)

fun ItemEstoque.toDisponibilidadeResponse() = DisponibilidadeResponse(ubs.toResponse(), status, quantidade, atualizadoEm)
