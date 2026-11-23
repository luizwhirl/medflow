package br.ufal.medflow.ubs

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HorarioFuncionamentoServiceTest {

    private val repository = mock(HorarioFuncionamentoRepository::class.java)

    private fun servico(instante: String) =
        HorarioFuncionamentoService(repository, Clock.fixed(Instant.parse(instante), ZoneOffset.UTC))

    private fun turno(ubsId: Long, dia: DiaSemana) =
        HorarioFuncionamento(ubsId = ubsId, diaSemana = dia, abre = LocalTime.of(7, 0), fecha = LocalTime.of(17, 0))

    @Test
    fun `usa o horario de Maceio, nao o UTC do relogio`() {
        given(repository.findByUbsIdInOrderByDiaSemanaAscAbreAsc(listOf(1L))).willReturn(listOf(turno(1, DiaSemana.SEXTA)))

        // Sexta, 19:30 UTC = 16:30 em Maceió (UTC-3): ainda aberta
        val tarde = servico("2026-11-20T19:30:00Z").buscar(listOf(1L)).getValue(1L)
        assertEquals(true, tarde.abertaAgora)
        assertEquals(LocalTime.of(17, 0), tarde.fechaAs)
        assertEquals(OffsetDateTime.parse("2026-11-20T17:00:00-03:00"), tarde.fechaEm)

        // Sábado, 02:00 UTC = sexta 23:00 em Maceió: fechada
        val noite = servico("2026-11-21T02:00:00Z").buscar(listOf(1L)).getValue(1L)
        assertEquals(false, noite.abertaAgora)
        assertNull(noite.fechaAs)
        assertNull(noite.fechaEm)
    }

    @Test
    fun `agrupa os turnos por UBS e deixa de fora quem nao tem horario`() {
        given(repository.findByUbsIdInOrderByDiaSemanaAscAbreAsc(listOf(1L, 2L, 3L)))
            .willReturn(listOf(turno(1, DiaSemana.SEGUNDA), turno(1, DiaSemana.SEXTA), turno(2, DiaSemana.SABADO)))

        // Sexta, 10:00 em Maceió
        val funcionamento = servico("2026-11-20T13:00:00Z").buscar(listOf(1L, 2L, 3L))

        assertEquals(setOf(1L, 2L), funcionamento.keys)
        assertEquals(listOf(DiaSemana.SEGUNDA, DiaSemana.SEXTA), funcionamento.getValue(1L).horarios.map { it.diaSemana })
        assertEquals(true, funcionamento.getValue(1L).abertaAgora)
        assertEquals(false, funcionamento.getValue(2L).abertaAgora)
    }

    @Test
    fun `lista vazia nao consulta o banco`() {
        assertEquals(emptyMap<Long, FuncionamentoUbs>(), servico("2026-11-20T13:00:00Z").buscar(emptyList()))
        verifyNoInteractions(repository)
    }
}
