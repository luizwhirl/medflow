package br.ufal.medflow.aviso

import br.ufal.medflow.listadesejo.ItemListaDesejoRepository
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeracaoAvisoServiceTest {
    private val lista = mock(ItemListaDesejoRepository::class.java)
    private val avisos = mock(AvisoRepository::class.java)
    private val instante = Instant.parse("2026-11-19T20:00:00Z")
    private val service = GeracaoAvisoService(lista, avisos, Clock.fixed(instante, ZoneOffset.UTC))
    private val primeiro = UUID.fromString("15da7427-97f6-4bf1-9d99-004af964e951")
    private val segundo = UUID.fromString("dcff9dbc-5ef6-4999-b880-83ad4e14e7db")
    private val ubs = Ubs(
        cnes = "9000101", nome = "UBS Centro", tipo = TipoUnidade.UBS,
        bairro = "Centro", endereco = "Rua A, 1", codigoIbgeMunicipio = "2704302",
        latitude = -9.5, longitude = -35.7, id = 10L,
    )
    private val medicamento = Medicamento(
        catmat = "BR0267690", nome = "Metformina", concentracao = "850 mg",
        formaFarmaceutica = "Comprimido", categoria = "Diabetes", id = 20L,
    )

    private fun lotes(): List<List<Aviso>> = mockingDetails(avisos).invocations
        .filter { it.method.name == "saveAll" }
        .map { call -> (call.arguments.single() as Iterable<*>).map { it as Aviso } }

    @Test
    fun `gera um aviso por aparelho interessado`() {
        given(lista.buscarDispositivosParaDisponibilidade(10L, 20L))
            .willReturn(listOf(primeiro, segundo, primeiro))

        assertEquals(2, service.registrarDisponibilidade(ubs, medicamento))
        val criados = lotes().single()
        assertEquals(setOf(primeiro, segundo), criados.map { it.dispositivoId }.toSet())
        assertEquals(criados[0].chaveEvento, criados[1].chaveEvento)
        assertTrue(criados.all { it.texto.contains("Metformina") && !it.lido })
        assertEquals(instante, criados.first().criadoEm)
    }

    @Test
    fun `ignora aparelhos com aviso nao lido da mesma disponibilidade`() {
        given(lista.buscarDispositivosParaDisponibilidade(10L, 20L))
            .willReturn(listOf(primeiro, segundo))
        given(avisos.buscarDispositivosComDisponibilidadeNaoLida(
            listOf(primeiro, segundo), TipoAviso.MEDICAMENTO_DISPONIVEL,
            "disponibilidade:10:20:",
        )).willReturn(listOf(primeiro))

        assertEquals(1, service.registrarDisponibilidade(ubs, medicamento))
        assertEquals(segundo, lotes().single().single().dispositivoId)
        verify(avisos).buscarDispositivosComDisponibilidadeNaoLida(
            listOf(primeiro, segundo), TipoAviso.MEDICAMENTO_DISPONIVEL,
            "disponibilidade:10:20:",
        )
    }

    @Test
    fun `nao gera avisos sem dispositivos interessados`() {
        given(lista.buscarDispositivosParaDisponibilidade(10L, 20L)).willReturn(emptyList())
        assertEquals(0, service.registrarDisponibilidade(ubs, medicamento))
        verifyNoInteractions(avisos)
    }

    @Test
    fun `exige ubs persistida`() {
        val semId = Ubs(
            cnes = "9000102", nome = "UBS Nova", tipo = TipoUnidade.UBS,
            bairro = "Centro", endereco = "Rua B", codigoIbgeMunicipio = "2704302",
            latitude = -9.5, longitude = -35.7,
        )
        assertFailsWith<IllegalArgumentException> { service.registrarDisponibilidade(semId, medicamento) }
        verifyNoInteractions(lista, avisos)
    }
}
