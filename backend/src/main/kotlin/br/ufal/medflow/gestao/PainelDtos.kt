package br.ufal.medflow.gestao

import java.time.Instant
import java.time.LocalDate

/** UBS ou medicamento resumidos, para as listas do painel. */
data class UbsResumoResponse(val id: Long, val nome: String)

data class MedicamentoResumoResponse(val id: Long, val nome: String)

/**
 * KPIs do painel no escopo do gestor (uma UBS ou a rede toda).
 * `ultimaSincronizacao` é a da UBS do escopo ou, na rede, a mais recente.
 */
data class IndicadoresResponse(
    val ubs: UbsEscopoResponse?,
    val restrito: Boolean,
    val ultimaSincronizacao: Instant?,
    val itensEmFalta: Int,
    val emFaltaComRemessa: Int,
    val abaixoDoMinimo: Int,
    val remessasACaminho: Int,
    val remessasAtrasadas: Int,
    val proximaRemessa: LocalDate?,
    val pacientesAguardando: Int,
    val medicamentosAguardados: Int,
    val lotesVencendo: Int,
    val ubsComSincronizacaoAtrasada: Int,
)

/** Contagens de itens de estoque no escopo. */
data class ContagemItens(val emFalta: Int, val emFaltaComRemessa: Int, val abaixoDoMinimo: Int)

data class ContagemRemessas(val aCaminho: Int, val atrasadas: Int, val proxima: LocalDate?)

data class ContagemDemanda(val pacientes: Int, val medicamentos: Int)

/** Sugestão de remanejamento: mover `quantidadeSugerida` da origem para o destino, na mesma cidade. */
data class RemanejamentoResponse(
    val medicamento: MedicamentoResumoResponse,
    val destino: UbsResumoResponse,
    val quantidadeDestino: Int,
    val minimo: Int,
    val origem: UbsResumoResponse,
    val quantidadeOrigem: Int,
    val distanciaKm: Double,
    val quantidadeSugerida: Int,
)

/** Pacientes que esperam um medicamento numa UBS onde ele não está disponível. */
data class DemandaItemResponse(
    val ubs: UbsResumoResponse,
    val medicamento: MedicamentoResumoResponse,
    val pacientesAguardando: Int,
    /** `null` quando a UBS ainda não tem posição desse medicamento. */
    val status: String?,
    val quantidade: Int?,
)

data class MaisProcuradoResponse(val medicamento: MedicamentoResumoResponse, val pacientes: Int)

data class DemandaReprimidaResponse(
    val itens: List<DemandaItemResponse>,
    val maisProcurados: List<MaisProcuradoResponse>,
)

data class LoteVencendoResponse(
    val loteId: Long,
    val numeroLote: String,
    val validade: LocalDate,
    val quantidade: Int,
    /** Negativo quando o lote já venceu. */
    val diasParaVencer: Long,
    val medicamento: MedicamentoResumoResponse,
    val ubs: UbsResumoResponse,
)

/** Item que pede atenção: sem saldo ou com saldo até o mínimo. `situacao`: EM_FALTA, ABAIXO_MINIMO ou PEDIDO_ENVIADO. */
data class ItemAtencaoResponse(
    val ubs: UbsResumoResponse,
    val medicamento: MedicamentoResumoResponse,
    val estoque: Int,
    val minimo: Int,
    /** Pacientes da lista de desejos que esperam o item nessa UBS. */
    val aguardandoAviso: Int,
    val situacao: String,
    /** Previsão da remessa a caminho, se houver (pode já ter passado: remessa atrasada). */
    val remessaPrevista: LocalDate?,
)

data class SincronizacaoAtrasadaResponse(
    val ubs: UbsEscopoResponse,
    /** `null` quando a UBS nunca sincronizou. */
    val ultimaSincronizacao: Instant?,
    val atrasoHoras: Long?,
)
