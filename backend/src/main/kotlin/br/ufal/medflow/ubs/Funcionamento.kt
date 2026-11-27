package br.ufal.medflow.ubs

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Fuso usado para calcular "aberta agora" (Maceió, sem horário de verão).
 * Limitação conhecida: vale para as UBS de Alagoas; unidades de estados com outro fuso (Acre, Amazonas,
 * Mato Grosso etc.) teriam `abertaAgora` deslocado. Quando o app sair de Alagoas, o fuso deve vir da UF da UBS.
 */
val FUSO_UBS: ZoneId = ZoneId.of("America/Maceio")

/**
 * Se a UBS está aberta num instante e, se estiver, quando fecha. [fechaEm] é `null` quando está fechada
 * e também quando funciona sem parar (turnos emendados a semana inteira, como numa unidade 24 h).
 */
data class SituacaoFuncionamento(val aberta: Boolean, val fechaEm: LocalDateTime?)

/** Regra de "aberta agora" a partir dos turnos cadastrados. Não depende de banco nem de relógio. */
object Funcionamento {

    /** Uma semana de turnos emendados à frente: a unidade não tem hora de fechar. */
    private const val DIAS_SEM_FECHAR = 7L

    /**
     * Situação da UBS em [agora] (horário local de Maceió).
     *
     * Turnos que viram a noite começam num dia e terminam no seguinte, por isso entram também os turnos de ontem.
     * Turno com `abre` igual a `fecha` dura 24 h. Turnos colados (7h–12h e 12h–17h, ou 18h–0h e 0h–6h) contam
     * como um só para [SituacaoFuncionamento.fechaEm].
     */
    fun em(turnos: List<HorarioFuncionamento>, agora: LocalDateTime): SituacaoFuncionamento {
        val hoje = agora.toLocalDate()
        // de ontem até uma semana à frente, para seguir os turnos emendados de uma unidade que não fecha
        val intervalos = (-1L..DIAS_SEM_FECHAR + 1).map { hoje.plusDays(it) }
            .flatMap { data ->
                turnos.filter { it.diaSemana.dayOfWeek == data.dayOfWeek }.map { t ->
                    val inicio = data.atTime(t.abre)
                    val fim = if (t.fecha.isAfter(t.abre)) data.atTime(t.fecha) else data.plusDays(1).atTime(t.fecha)
                    inicio to fim
                }
            }

        val atual = intervalos.firstOrNull { (inicio, fim) -> !agora.isBefore(inicio) && agora.isBefore(fim) }
            ?: return SituacaoFuncionamento(aberta = false, fechaEm = null)

        val limite = agora.plusDays(DIAS_SEM_FECHAR)
        var fim = atual.second
        while (fim.isBefore(limite)) {
            val emendado = intervalos.firstOrNull { (inicio, f) -> !inicio.isAfter(fim) && f.isAfter(fim) } ?: break
            fim = emendado.second
        }
        return SituacaoFuncionamento(aberta = true, fechaEm = if (fim.isBefore(limite)) fim else null)
    }
}
