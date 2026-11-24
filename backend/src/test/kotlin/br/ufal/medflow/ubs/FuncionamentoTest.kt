package br.ufal.medflow.ubs

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals

/** Regra de "aberta agora". 20/11/2026 é uma sexta-feira. */
class FuncionamentoTest {

    private fun turno(dia: DiaSemana, abre: String, fecha: String) =
        HorarioFuncionamento(ubsId = 1, diaSemana = dia, abre = LocalTime.parse(abre), fecha = LocalTime.parse(fecha))

    private val segASex = listOf(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA, DiaSemana.QUINTA, DiaSemana.SEXTA)
        .map { turno(it, "07:00", "17:00") }

    private fun sexta(hora: String) = LocalDateTime.parse("2026-11-20T$hora")

    /** Aberta, fechando em [fechaEm] (data e hora, `2026-11-20T17:00`). */
    private fun aberta(fechaEm: String) = SituacaoFuncionamento(aberta = true, fechaEm = LocalDateTime.parse(fechaEm))
    private val semFechar = SituacaoFuncionamento(aberta = true, fechaEm = null)
    private val fechada = SituacaoFuncionamento(aberta = false, fechaEm = null)

    @Test
    fun `dentro do horario esta aberta e informa quando fecha`() {
        assertEquals(aberta("2026-11-20T17:00"), Funcionamento.em(segASex, sexta("10:30")))
    }

    @Test
    fun `abre no minuto de abrir e fecha no minuto de fechar`() {
        assertEquals(aberta("2026-11-20T17:00"), Funcionamento.em(segASex, sexta("07:00")))
        assertEquals(fechada, Funcionamento.em(segASex, sexta("06:59")))
        assertEquals(fechada, Funcionamento.em(segASex, sexta("17:00")))
    }

    @Test
    fun `no fim de semana fica fechada se nao houver turno`() {
        assertEquals(fechada, Funcionamento.em(segASex, LocalDateTime.parse("2026-11-21T10:00"))) // sábado
        assertEquals(fechada, Funcionamento.em(segASex, LocalDateTime.parse("2026-11-22T10:00"))) // domingo
    }

    @Test
    fun `turno de sabado vale so no sabado`() {
        val turnos = segASex + turno(DiaSemana.SABADO, "08:00", "12:00")
        assertEquals(aberta("2026-11-21T12:00"), Funcionamento.em(turnos, LocalDateTime.parse("2026-11-21T09:00")))
        assertEquals(fechada, Funcionamento.em(turnos, LocalDateTime.parse("2026-11-22T09:00")))
    }

    @Test
    fun `turno que vira a noite fecha no dia seguinte`() {
        val plantao = listOf(turno(DiaSemana.SEXTA, "19:00", "07:00"))
        // na noite de sexta, o fechamento é no sábado: a data deixa isso explícito
        assertEquals(aberta("2026-11-21T07:00"), Funcionamento.em(plantao, sexta("23:30")))
        assertEquals(aberta("2026-11-21T07:00"), Funcionamento.em(plantao, LocalDateTime.parse("2026-11-21T02:00")))
        assertEquals(fechada, Funcionamento.em(plantao, LocalDateTime.parse("2026-11-21T07:00")))
        assertEquals(fechada, Funcionamento.em(plantao, sexta("02:00"))) // madrugada de sexta: quinta não tem plantão
    }

    @Test
    fun `turnos emendados contam como um so para a hora de fechar`() {
        val turnos = listOf(turno(DiaSemana.SEXTA, "07:00", "12:00"), turno(DiaSemana.SEXTA, "12:00", "17:00"))
        assertEquals(aberta("2026-11-20T17:00"), Funcionamento.em(turnos, sexta("08:00")))
    }

    @Test
    fun `intervalo de almoco fecha a unidade`() {
        val turnos = listOf(turno(DiaSemana.SEXTA, "07:00", "11:00"), turno(DiaSemana.SEXTA, "13:00", "17:00"))
        assertEquals(aberta("2026-11-20T11:00"), Funcionamento.em(turnos, sexta("08:00")))
        assertEquals(fechada, Funcionamento.em(turnos, sexta("12:00")))
    }

    @Test
    fun `turno com abre igual a fecha dura 24 horas`() {
        val turnos = listOf(turno(DiaSemana.SEXTA, "00:00", "00:00"))
        assertEquals(aberta("2026-11-21T00:00"), Funcionamento.em(turnos, sexta("00:00")))
        assertEquals(aberta("2026-11-21T00:00"), Funcionamento.em(turnos, sexta("23:59")))
        assertEquals(fechada, Funcionamento.em(turnos, LocalDateTime.parse("2026-11-21T00:00")))
    }

    @Test
    fun `unidade 24 horas todos os dias nao tem hora de fechar`() {
        val sempre = DiaSemana.entries.map { turno(it, "00:00", "00:00") }
        assertEquals(semFechar, Funcionamento.em(sempre, sexta("03:00")))
        assertEquals(semFechar, Funcionamento.em(sempre, LocalDateTime.parse("2026-11-22T23:59"))) // domingo
    }

    @Test
    fun `24 horas so nos dias uteis fecha no sabado a meia-noite`() {
        val diasUteis = segASex.map { turno(it.diaSemana, "00:00", "00:00") }
        assertEquals(aberta("2026-11-21T00:00"), Funcionamento.em(diasUteis, LocalDateTime.parse("2026-11-16T09:00")))
    }

    @Test
    fun `plantoes noturnos emendados a semana inteira tambem nao tem hora de fechar`() {
        val sempre = DiaSemana.entries.flatMap { listOf(turno(it, "07:00", "19:00"), turno(it, "19:00", "07:00")) }
        assertEquals(semFechar, Funcionamento.em(sempre, sexta("20:00")))
    }

    @Test
    fun `sem turnos fica fechada`() {
        assertEquals(fechada, Funcionamento.em(emptyList(), sexta("10:00")))
    }
}
