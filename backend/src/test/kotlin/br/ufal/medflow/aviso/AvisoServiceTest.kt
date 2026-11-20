package br.ufal.medflow.aviso

import br.ufal.medflow.auth.CredenciaisInvalidasException
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Testes das regras de consulta e leitura dos avisos. */
class AvisoServiceTest {

    private val avisos = mock(AvisoRepository::class.java)
    private val usuarios = mock(UsuarioRepository::class.java)

    private val service = AvisoService(
        avisos,
        usuarios,
    )

    private val cidadao = Usuario(
        nome = "Cidadão Teste",
        email = "cidadao-avisos@teste.com",
        senhaHash = "hash-teste",
        papel = PapelUsuario.CIDADAO,
        id = 1L,
    )

    @Test
    fun `lista somente os avisos consultados para o usuario autenticado`() {
        given(usuarios.existsById(1L)).willReturn(true)

        val aviso = Aviso(
            usuario = cidadao,
            tipo = TipoAviso.MEDICAMENTO_DISPONIVEL,
            titulo = "Medicamento disponível",
            texto = "A Metformina está disponível na UBS Centro.",
            chaveEvento = "disponibilidade:1:2:3",
            criadoEm = Instant.parse("2026-11-19T20:00:00Z"),
            id = 10L,
        )

        given(
            avisos.findAllByUsuarioIdOrderByCriadoEmDescIdDesc(1L),
        ).willReturn(listOf(aviso))

        val resultado = service.listar(1L)

        assertEquals(1, resultado.size)
        assertEquals(10L, resultado.first().id)
        assertEquals(
            TipoAviso.MEDICAMENTO_DISPONIVEL,
            resultado.first().tipo,
        )
        assertEquals("Medicamento disponível", resultado.first().titulo)
        assertEquals(
            "A Metformina está disponível na UBS Centro.",
            resultado.first().texto,
        )
        assertEquals(false, resultado.first().lido)
        assertEquals(
            Instant.parse("2026-11-19T20:00:00Z"),
            resultado.first().criadoEm,
        )

        verify(avisos).findAllByUsuarioIdOrderByCriadoEmDescIdDesc(1L)
    }

    @Test
    fun `lista vazia quando cidadao nao possui avisos`() {
        given(usuarios.existsById(1L)).willReturn(true)

        given(
            avisos.findAllByUsuarioIdOrderByCriadoEmDescIdDesc(1L),
        ).willReturn(emptyList())

        val resultado = service.listar(1L)

        assertTrue(resultado.isEmpty())
        verify(avisos).findAllByUsuarioIdOrderByCriadoEmDescIdDesc(1L)
    }

    @Test
    fun `marca todos os avisos nao lidos do usuario`() {
        given(usuarios.existsById(1L)).willReturn(true)
        given(avisos.marcarTodosComoLidos(1L)).willReturn(3)

        val resultado = service.marcarTodosComoLidos(1L)

        assertEquals(3, resultado.atualizados)
        verify(avisos).marcarTodosComoLidos(1L)
    }

    @Test
    fun `retorna zero quando nao existem avisos para marcar`() {
        given(usuarios.existsById(1L)).willReturn(true)
        given(avisos.marcarTodosComoLidos(1L)).willReturn(0)

        val resultado = service.marcarTodosComoLidos(1L)

        assertEquals(0, resultado.atualizados)
        verify(avisos).marcarTodosComoLidos(1L)
    }

    @Test
    fun `impede listagem quando usuario foi excluido`() {
        given(usuarios.existsById(1L)).willReturn(false)

        assertFailsWith<CredenciaisInvalidasException> {
            service.listar(1L)
        }

        verifyNoInteractions(avisos)
    }

    @Test
    fun `impede marcar avisos quando usuario foi excluido`() {
        given(usuarios.existsById(1L)).willReturn(false)

        assertFailsWith<CredenciaisInvalidasException> {
            service.marcarTodosComoLidos(1L)
        }

        verifyNoInteractions(avisos)
    }
}
