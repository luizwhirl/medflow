package br.ufal.medflow.ubs

import com.fasterxml.jackson.annotation.JsonFormat
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime

data class HorarioResponse(
    val diaSemana: DiaSemana,
    @get:JsonFormat(pattern = "HH:mm") val abre: LocalTime,
    @get:JsonFormat(pattern = "HH:mm") val fecha: LocalTime,
)

/**
 * Horários de uma UBS e a situação dela agora. [fechaEm] é a data e hora de fechamento (pode ser no dia
 * seguinte, num turno que vira a noite); [fechaAs] é só a hora, para exibir. Os dois ficam `null` quando a
 * unidade está fechada ou funciona sem parar.
 */
data class FuncionamentoUbs(
    val horarios: List<HorarioResponse>,
    val abertaAgora: Boolean,
    val fechaAs: LocalTime?,
    val fechaEm: OffsetDateTime? = null,
)

/** Turno já validado, pronto para gravar. */
data class Turno(val diaSemana: DiaSemana, val abre: LocalTime, val fecha: LocalTime)

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
                    fechaAs = situacao.fechaEm?.toLocalTime(),
                    fechaEm = situacao.fechaEm?.atZone(FUSO_UBS)?.toOffsetDateTime(),
                )
            }
    }

    /**
     * Troca todos os turnos da UBS pelos informados e devolve o funcionamento já recalculado
     * (`null` quando a lista vem vazia, ou seja, a UBS fica sem horário cadastrado).
     */
    @Transactional
    fun substituir(ubsId: Long, turnos: List<Turno>): FuncionamentoUbs? {
        horarioRepository.apagarDaUbs(ubsId)
        horarioRepository.saveAll(turnos.map { HorarioFuncionamento(ubsId, it.diaSemana, it.abre, it.fecha) })
        horarioRepository.flush()
        return buscar(listOf(ubsId))[ubsId]
    }
}
