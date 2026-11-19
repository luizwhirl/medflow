package br.ufal.medflow.ubs

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Fuso das UBS atendidas (Maceió, sem horário de verão). */
val FUSO_UBS: ZoneId = ZoneId.of("America/Maceio")

/** Se a UBS está aberta num instante e, se estiver, a que horas fecha. */
data class SituacaoFuncionamento(val aberta: Boolean, val fechaAs: LocalTime?)

/** Regra de "aberta agora" a partir dos turnos cadastrados. Não depende de banco nem de relógio. */
object Funcionamento {

    /**
     * Situação da UBS em [agora] (horário local de Maceió).
     *
     * Turnos que viram a noite começam num dia e terminam no seguinte, por isso entram também os turnos de ontem.
     * Turnos colados (7h–12h e 12h–17h, ou 18h–0h e 0h–6h) contam como um só para [SituacaoFuncionamento.fechaAs].
     */
    fun em(turnos: List<HorarioFuncionamento>, agora: LocalDateTime): SituacaoFuncionamento {
        val hoje = agora.toLocalDate()
        val intervalos = listOf(hoje.minusDays(1), hoje, hoje.plusDays(1))
            .flatMap { data ->
                turnos.filter { it.diaSemana.dayOfWeek == data.dayOfWeek }.map { t ->
                    val inicio = data.atTime(t.abre)
                    val fim = if (t.fecha.isAfter(t.abre)) data.atTime(t.fecha) else data.plusDays(1).atTime(t.fecha)
                    inicio to fim
                }
            }

        val atual = intervalos.firstOrNull { (inicio, fim) -> !agora.isBefore(inicio) && agora.isBefore(fim) }
            ?: return SituacaoFuncionamento(aberta = false, fechaAs = null)

        var fim = atual.second
        while (true) {
            val emendado = intervalos.firstOrNull { (inicio, f) -> !inicio.isAfter(fim) && f.isAfter(fim) } ?: break
            fim = emendado.second
        }
        return SituacaoFuncionamento(aberta = true, fechaAs = fim.toLocalTime())
    }
}
