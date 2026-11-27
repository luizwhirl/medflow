package br.ufal.medflow.ubs

import br.ufal.medflow.common.PaginaResponse
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.common.toPaginaResponse
import br.ufal.medflow.estoque.ItemEstoque
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.ItemEstoqueResponse
import br.ufal.medflow.estoque.ResumoEstoqueUbsResponse
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.estoque.toItemResponse
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UbsEstoqueService(
    private val ubsRepository: UbsRepository,
    private val itemEstoqueRepository: ItemEstoqueRepository,
) {

    /** Estoque da UBS em ordem de nome do medicamento, paginado. */
    fun listarItens(
        ubsId: Long,
        status: StatusEstoque?,
        pagina: Pageable,
    ): PaginaResponse<ItemEstoqueResponse> {
        verificarUbs(ubsId)

        val itens = if (status == null) {
            itemEstoqueRepository.findByUbs(ubsId, pagina)
        } else {
            itemEstoqueRepository.findByUbsEStatus(ubsId, status, pagina)
        }
        return itens.toPaginaResponse { it.toItemResponse() }
    }

    fun obterResumo(
        ubsId: Long,
        status: StatusEstoque?,
    ): ResumoEstoqueUbsResponse {
        verificarUbs(ubsId)

        val contagens = itemEstoqueRepository.contarPorStatusDaUbs(ubsId)
            .associate { it.status to it.total.toInt() }

        val itens = buscarItens(ubsId, status)

        return ResumoEstoqueUbsResponse(
            disponivel = contagens[StatusEstoque.DISPONIVEL] ?: 0,
            reservado = contagens[StatusEstoque.RESERVADO] ?: 0,
            emFalta = contagens[StatusEstoque.EM_FALTA] ?: 0,
            itens = itens.map { it.toItemResponse() },
        )
    }

    private fun verificarUbs(ubsId: Long) {
        if (!ubsRepository.existsById(ubsId)) {
            throw RecursoNaoEncontradoException("UBS", ubsId)
        }
    }

    private fun buscarItens(
        ubsId: Long,
        status: StatusEstoque?,
    ): List<ItemEstoque> =
        if (status == null) {
            itemEstoqueRepository.findByUbs(ubsId)
        } else {
            itemEstoqueRepository.findByUbsEStatus(ubsId, status)
        }
}
