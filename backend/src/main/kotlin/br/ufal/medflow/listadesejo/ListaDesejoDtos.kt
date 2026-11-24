package br.ufal.medflow.listadesejo

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.Instant

/** Dados necessários para adicionar um medicamento à lista de desejos. */
data class CriarItemListaDesejoRequest(
    @field:Positive
    val ubsId: Long,

    @field:Positive
    val medicamentoId: Long,

    val notificar: Boolean = true,
)

/** Permite ativar ou desativar o aviso de disponibilidade. */
data class AtualizarNotificacaoRequest(
    @field:NotNull
    val notificar: Boolean?,
)

/** Dados de um item da lista de desejos retornados pela API. */
data class ItemListaDesejoResponse(
    val id: Long,
    val ubsId: Long,
    val ubsNome: String,
    val medicamentoId: Long,
    val medicamentoNome: String,
    /** Evita que o front busque o medicamento só para mostrar a concentração. */
    val medicamentoConcentracao: String,
    val notificar: Boolean,
    val criadoEm: Instant,
)

/** Converte a entidade para o formato público da API. */
fun ItemListaDesejo.toResponse() = ItemListaDesejoResponse(
    id = requireNotNull(id),
    ubsId = requireNotNull(ubs.id),
    ubsNome = ubs.nome,
    medicamentoId = requireNotNull(medicamento.id),
    medicamentoNome = medicamento.nome,
    medicamentoConcentracao = medicamento.concentracao,
    notificar = notificar,
    criadoEm = criadoEm,
)
