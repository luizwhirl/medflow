package br.ufal.medflow.ubs

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.ResumoEstoqueUbsResponse
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.estoque.toItemResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UbsEstoqueService(
    private val ubsRepository: UbsRepository,
    private val itemEstoqueRepository: ItemEstoqueRepository,
) {

    fun obterResumo(
        ubsId: Long,
        status: StatusEstoque?,
    ): ResumoEstoqueUbsResponse {
        if (!ubsRepository.existsById(ubsId)) {
            throw RecursoNaoEncontradoException("UBS", ubsId)
        }

        val contagens = itemEstoqueRepository.contarPorStatusDaUbs(ubsId)
            .associate { it.status to it.total.toInt() }

        val itens = if (status == null) {
            itemEstoqueRepository.findByUbs(ubsId)
        } else {
            itemEstoqueRepository.findByUbsEStatus(ubsId, status)
        }

        return ResumoEstoqueUbsResponse(
            disponivel = contagens[StatusEstoque.DISPONIVEL] ?: 0,
            reservado = contagens[StatusEstoque.RESERVADO] ?: 0,
            emFalta = contagens[StatusEstoque.EM_FALTA] ?: 0,
            itens = itens.map { it.toItemResponse() },
        )
    }
}
