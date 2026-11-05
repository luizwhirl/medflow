package br.ufal.medflow.medicamento

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.ubs.UbsRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.assertEquals

/** Testes do resumo de disponibilidade, sem banco. */
class MedicamentoDisponibilidadeServiceTest {

    private val medicamentoRepository = mock(MedicamentoRepository::class.java)
    private val ubsRepository = mock(UbsRepository::class.java)
    private val itemEstoqueRepository = mock(ItemEstoqueRepository::class.java)

    private val service = MedicamentoDisponibilidadeService(
        medicamentoRepository,
        ubsRepository,
        itemEstoqueRepository,
    )

    @Test
    fun `resumo retorna contagens das unidades`() {
        given(medicamentoRepository.existsById(1L)).willReturn(true)
        given(ubsRepository.count()).willReturn(7L)
        given(itemEstoqueRepository.contarUnidadesComItem(1L))
            .willReturn(6L)

        val resumo = service.obterResumo(1L)

        assertEquals(7L, resumo.totalUnidades)
        assertEquals(6L, resumo.unidadesComItem)

        verify(ubsRepository).count()
        verify(itemEstoqueRepository).contarUnidadesComItem(1L)
    }

    @Test
    fun `medicamento sem registros retorna contagem zero`() {
        given(medicamentoRepository.existsById(2L)).willReturn(true)
        given(ubsRepository.count()).willReturn(7L)
        given(itemEstoqueRepository.contarUnidadesComItem(2L))
            .willReturn(0L)

        val resumo = service.obterResumo(2L)

        assertEquals(7L, resumo.totalUnidades)
        assertEquals(0L, resumo.unidadesComItem)
    }

    @Test
    fun `medicamento inexistente retorna excecao`() {
        given(medicamentoRepository.existsById(999L)).willReturn(false)

        assertThrows<RecursoNaoEncontradoException> {
            service.obterResumo(999L)
        }

        verifyNoInteractions(ubsRepository, itemEstoqueRepository)
    }
}
