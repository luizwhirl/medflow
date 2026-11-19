package br.ufal.medflow.ubs

import com.fasterxml.jackson.annotation.JsonFormat
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime

data class HorarioResponse(
    val diaSemana: DiaSemana,
    @get:JsonFormat(pattern = "HH:mm") val abre: LocalTime,
    @get:JsonFormat(pattern = "HH:mm") val fecha: LocalTime,
)

/** Horários de uma UBS e a situação dela agora. */
data class FuncionamentoUbs(
    val horarios: List<HorarioResponse>,
    val abertaAgora: Boolean,
    val fechaAs: LocalTime?,
)

@Service
@Transactional(readOnly = true)
class HorarioFuncionamentoService(
    private val horarioRepository: HorarioFuncionamentoRepository,
    private val relogio: Clock,
) {

    /**
     * Funcionamento das UBS de [ubsIds] no horário atual de Maceió, numa única consulta.
     * UBS sem horário cadastrado ficam fora do mapa (situação desconhecida).
     */
    fun buscar(ubsIds: Collection<Long>): Map<Long, FuncionamentoUbs> {
        if (ubsIds.isEmpty()) return emptyMap()
        val agora = LocalDateTime.now(relogio.withZone(FUSO_UBS))
        return horarioRepository.findByUbsIdInOrderByDiaSemanaAscAbreAsc(ubsIds)
            .groupBy { it.ubsId }
            .mapValues { (_, turnos) ->
                val situacao = Funcionamento.em(turnos, agora)
                FuncionamentoUbs(
                    horarios = turnos.map { HorarioResponse(it.diaSemana, it.abre, it.fecha) },
                    abertaAgora = situacao.aberta,
                    fechaAs = situacao.fechaAs,
                )
            }
    }
}
