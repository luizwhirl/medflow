package br.ufal.medflow.aviso

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** Testa que a consulta e a leitura sempre usam o UUID informado. */
class AvisoServiceTest {
    private val repositorio = mock(AvisoRepository::class.java)
    private val service = AvisoService(repositorio)
    private val dispositivo = UUID.fromString("15da7427-97f6-4bf1-9d99-004af964e951")

    @Test
    fun `lista somente os avisos retornados para o aparelho`() {
        val aviso = Aviso(
            dispositivoId = dispositivo,
            tipo = TipoAviso.MEDICAMENTO_DISPONIVEL,
            titulo = "Medicamento disponível",
            texto = "Teste",
            criadoEm = Instant.parse("2026-11-19T10:00:00Z"),
            chaveEvento = "evento-1",
            id = 42L,
        )
        given(repositorio.findTop50ByDispositivoIdOrderByCriadoEmDescIdDesc(dispositivo))
            .willReturn(listOf(aviso))

        assertEquals(42L, service.listar(dispositivo).single().id)
        verify(repositorio).findTop50ByDispositivoIdOrderByCriadoEmDescIdDesc(dispositivo)
    }

    @Test
    fun `contador considera todos os avisos nao lidos diretamente no banco`() {
        given(repositorio.countByDispositivoIdAndLidoFalse(dispositivo)).willReturn(57L)

        assertEquals(57L, service.contarNaoLidos(dispositivo).naoLidos)
        verify(repositorio).countByDispositivoIdAndLidoFalse(dispositivo)
    }

    @Test
    fun `marca apenas avisos do aparelho e retorna total atualizado`() {
        given(repositorio.marcarTodosComoLidos(dispositivo)).willReturn(3)

        assertEquals(3, service.marcarTodosComoLidos(dispositivo).atualizados)
        verify(repositorio).marcarTodosComoLidos(dispositivo)
    }
}
