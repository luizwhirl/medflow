package br.ufal.medflow.remessa

import java.time.Instant
import java.time.LocalDate

/** Uma etapa da linha do tempo. `ocorridoEm` é nulo enquanto a remessa não chegou nela. */
data class EtapaRastreamentoResponse(
    val etapa: EtapaRemessa,
    val ocorridoEm: Instant?,
)

data class RemessaRastreamentoResponse(
    val id: Long,
    val quantidade: Int,
    val previsaoChegada: LocalDate,
    val etapaAtual: EtapaRemessa,
    /** Verdadeiro quando a remessa já chegou à etapa DISPONIVEL. */
    val concluida: Boolean,
    /** Sempre com as cinco etapas, na ordem em que acontecem. */
    val linhaDoTempo: List<EtapaRastreamentoResponse>,
)

/** `remessa` é nula quando não há remessa desse medicamento para a UBS. */
data class RastreamentoResponse(
    val medicamentoId: Long,
    val ubsId: Long,
    val remessa: RemessaRastreamentoResponse?,
)
