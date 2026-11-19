package br.ufal.medflow.ubs

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.DayOfWeek
import java.time.LocalTime

/** Dia da semana na API, na ordem ISO-8601 (segunda primeiro). */
enum class DiaSemana(val dayOfWeek: DayOfWeek) {
    SEGUNDA(DayOfWeek.MONDAY),
    TERCA(DayOfWeek.TUESDAY),
    QUARTA(DayOfWeek.WEDNESDAY),
    QUINTA(DayOfWeek.THURSDAY),
    SEXTA(DayOfWeek.FRIDAY),
    SABADO(DayOfWeek.SATURDAY),
    DOMINGO(DayOfWeek.SUNDAY),
}

/** Grava o dia como o número ISO-8601 (1 = segunda ... 7 = domingo), como na coluna `dia_semana`. */
@Converter
class DiaSemanaConverter : AttributeConverter<DiaSemana, Short> {
    override fun convertToDatabaseColumn(dia: DiaSemana?): Short? = dia?.dayOfWeek?.value?.toShort()

    override fun convertToEntityAttribute(valor: Short?): DiaSemana? =
        valor?.let { v -> DiaSemana.entries.first { it.dayOfWeek.value == v.toInt() } }
}

/**
 * Um turno de atendimento da UBS num dia da semana. Se [fecha] for antes de [abre], o turno termina no dia
 * seguinte (ex.: 19:00 às 07:00).
 */
@Entity
@Table(name = "horario_funcionamento")
class HorarioFuncionamento(
    @Column(name = "ubs_id", nullable = false)
    var ubsId: Long,

    @Convert(converter = DiaSemanaConverter::class)
    @Column(name = "dia_semana", nullable = false)
    var diaSemana: DiaSemana,

    @Column(nullable = false)
    var abre: LocalTime,

    @Column(nullable = false)
    var fecha: LocalTime,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)

interface HorarioFuncionamentoRepository : JpaRepository<HorarioFuncionamento, Long> {
    fun findByUbsIdInOrderByDiaSemanaAscAbreAsc(ubsIds: Collection<Long>): List<HorarioFuncionamento>
}
