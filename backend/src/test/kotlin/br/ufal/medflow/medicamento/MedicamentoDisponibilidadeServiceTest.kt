package br.ufal.medflow.medicamento

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.StatusEstoque
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
    fun `resumo retorna apenas unidades com estoque disponivel`() {
        given(medicamentoRepository.existsById(1L)).willReturn(true)
        given(ubsRepository.count()).willReturn(7L)
        given(
            itemEstoqueRepository.contarUnidadesDisponiveis(
                1L,
                StatusEstoque.DISPONIVEL,
            ),
        ).willReturn(3L)

        val resumo = service.obterResumo(1L)

        assertEquals(7L, resumo.totalUnidades)
        assertEquals(3L, resumo.unidadesDisponiveis)

        verify(ubsRepository).count()
        verify(itemEstoqueRepository).contarUnidadesDisponiveis(
            1L,
            StatusEstoque.DISPONIVEL,
        )
    }

    @Test
    fun `resumo filtra unidades pelo municipio`() {
        given(medicamentoRepository.existsById(1L)).willReturn(true)
        given(ubsRepository.countByCodigoIbgeMunicipio("2704302"))
            .willReturn(5L)
        given(
            itemEstoqueRepository.contarUnidadesDisponiveisPorMunicipio(
                1L,
                "2704302",
                StatusEstoque.DISPONIVEL,
            ),
        ).willReturn(2L)

        val resumo = service.obterResumo(1L, "2704302")

        assertEquals(5L, resumo.totalUnidades)
        assertEquals(2L, resumo.unidadesDisponiveis)

        verify(ubsRepository).countByCodigoIbgeMunicipio("2704302")
        verify(itemEstoqueRepository).contarUnidadesDisponiveisPorMunicipio(
            1L,
            "2704302",
            StatusEstoque.DISPONIVEL,
        )
    }

    @Test
    fun `medicamento sem estoque disponivel retorna zero`() {
        given(medicamentoRepository.existsById(2L)).willReturn(true)
        given(ubsRepository.count()).willReturn(7L)
        given(
            itemEstoqueRepository.contarUnidadesDisponiveis(
                2L,
                StatusEstoque.DISPONIVEL,
            ),
        ).willReturn(0L)

        val resumo = service.obterResumo(2L)

        assertEquals(7L, resumo.totalUnidades)
        assertEquals(0L, resumo.unidadesDisponiveis)
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
