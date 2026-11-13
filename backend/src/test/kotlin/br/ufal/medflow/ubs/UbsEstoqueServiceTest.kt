package br.ufal.medflow.ubs

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ContagemEstoquePorStatus
import br.ufal.medflow.estoque.ItemEstoque
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.medicamento.Medicamento
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Testes das regras de resumo do estoque, sem banco. */
class UbsEstoqueServiceTest {

    private val ubsRepository = mock(UbsRepository::class.java)
    private val itemEstoqueRepository = mock(ItemEstoqueRepository::class.java)

    private val service = UbsEstoqueService(
        ubsRepository,
        itemEstoqueRepository,
    )

    private val ubs = Ubs(
        cnes = "9000101",
        nome = "UBS Teste",
        tipo = TipoUnidade.UBS,
        bairro = "Centro",
        endereco = "Rua Teste, 100",
        codigoIbgeMunicipio = "2704302",
        latitude = -9.66,
        longitude = -35.73,
        id = 1L,
    )

    private fun criarItem(
        codigo: String,
        nome: String,
        status: StatusEstoque,
    ) = ItemEstoque(
        ubs = ubs,
        medicamento = Medicamento(
            catmat = codigo,
            nome = nome,
            concentracao = "500 mg",
            formaFarmaceutica = "comprimido",
            categoria = "Teste",
            id = codigo.removePrefix("TEST").toLong(),
        ),
        status = status,
        quantidade = if (status == StatusEstoque.EM_FALTA) 0 else 10,
    )

    private val itens = listOf(
        criarItem("TEST001", "Medicamento A", StatusEstoque.DISPONIVEL),
        criarItem("TEST002", "Medicamento B", StatusEstoque.DISPONIVEL),
        criarItem("TEST003", "Medicamento C", StatusEstoque.RESERVADO),
        criarItem("TEST004", "Medicamento D", StatusEstoque.EM_FALTA),
    )

    private val contagens = listOf(
        ContagemEstoquePorStatus(StatusEstoque.DISPONIVEL, 2L),
        ContagemEstoquePorStatus(StatusEstoque.RESERVADO, 1L),
        ContagemEstoquePorStatus(StatusEstoque.EM_FALTA, 1L),
    )

    @Test
    fun `resumo contabiliza todos os status corretamente`() {
        given(ubsRepository.existsById(1L)).willReturn(true)
        given(itemEstoqueRepository.contarPorStatusDaUbs(1L))
            .willReturn(contagens)
        given(itemEstoqueRepository.findByUbs(1L))
            .willReturn(itens)

        val resumo = service.obterResumo(1L, null)

        assertEquals(2, resumo.disponivel)
        assertEquals(1, resumo.reservado)
        assertEquals(1, resumo.emFalta)
        assertEquals(4, resumo.itens.size)

        verify(itemEstoqueRepository).contarPorStatusDaUbs(1L)
        verify(itemEstoqueRepository).findByUbs(1L)
    }

    @Test
    fun `filtro retorna apenas itens do status sem alterar contagens`() {
        given(ubsRepository.existsById(1L)).willReturn(true)
        given(itemEstoqueRepository.contarPorStatusDaUbs(1L))
            .willReturn(contagens)
        given(
            itemEstoqueRepository.findByUbsEStatus(
                1L,
                StatusEstoque.DISPONIVEL,
            ),
        ).willReturn(itens.take(2))

        val resumo = service.obterResumo(1L, StatusEstoque.DISPONIVEL)

        assertEquals(2, resumo.disponivel)
        assertEquals(1, resumo.reservado)
        assertEquals(1, resumo.emFalta)
        assertEquals(2, resumo.itens.size)
        assertEquals(
            listOf("Medicamento A", "Medicamento B"),
            resumo.itens.map { it.medicamento.nome },
        )
        assertTrue(resumo.itens.all { it.status == StatusEstoque.DISPONIVEL })

        verify(itemEstoqueRepository).contarPorStatusDaUbs(1L)
        verify(itemEstoqueRepository).findByUbsEStatus(
            1L,
            StatusEstoque.DISPONIVEL,
        )
        verify(itemEstoqueRepository, never()).findByUbs(1L)
    }

    @Test
    fun `status sem itens retorna contagem zero`() {
        given(ubsRepository.existsById(1L)).willReturn(true)
        given(itemEstoqueRepository.contarPorStatusDaUbs(1L))
            .willReturn(
                listOf(
                    ContagemEstoquePorStatus(StatusEstoque.DISPONIVEL, 2L),
                ),
            )
        given(itemEstoqueRepository.findByUbs(1L))
            .willReturn(itens.take(2))

        val resumo = service.obterResumo(1L, null)

        assertEquals(2, resumo.disponivel)
        assertEquals(0, resumo.reservado)
        assertEquals(0, resumo.emFalta)
        assertEquals(2, resumo.itens.size)
    }

    @Test
    fun `UBS inexistente retorna excecao`() {
        given(ubsRepository.existsById(999L)).willReturn(false)

        assertThrows<RecursoNaoEncontradoException> {
            service.obterResumo(999L, null)
        }

        verifyNoInteractions(itemEstoqueRepository)
    }

    @Test
    fun `listagem sem filtro retorna todos os itens`() {
        given(ubsRepository.existsById(1L)).willReturn(true)
        given(itemEstoqueRepository.findByUbs(1L))
            .willReturn(itens)

        val resultado = service.listarItens(1L, null)

        assertEquals(4, resultado.size)
        assertEquals(
            listOf(
                "Medicamento A",
                "Medicamento B",
                "Medicamento C",
                "Medicamento D",
            ),
            resultado.map { it.medicamento.nome },
        )

        verify(itemEstoqueRepository).findByUbs(1L)
        verify(itemEstoqueRepository, never()).contarPorStatusDaUbs(1L)
    }

    @Test
    fun `listagem com filtro consulta apenas o status informado`() {
        given(ubsRepository.existsById(1L)).willReturn(true)
        given(
            itemEstoqueRepository.findByUbsEStatus(
                1L,
                StatusEstoque.DISPONIVEL,
            ),
        ).willReturn(itens.take(2))

        val resultado = service.listarItens(1L, StatusEstoque.DISPONIVEL)

        assertEquals(2, resultado.size)
        assertTrue(resultado.all { it.status == StatusEstoque.DISPONIVEL })

        verify(itemEstoqueRepository).findByUbsEStatus(
            1L,
            StatusEstoque.DISPONIVEL,
        )
        verify(itemEstoqueRepository, never()).findByUbs(1L)
        verify(itemEstoqueRepository, never()).contarPorStatusDaUbs(1L)
    }

    @Test
    fun `listagem de UBS inexistente retorna excecao`() {
        given(ubsRepository.existsById(999L)).willReturn(false)

        assertThrows<RecursoNaoEncontradoException> {
            service.listarItens(999L, null)
        }

        verifyNoInteractions(itemEstoqueRepository)
    }
}
