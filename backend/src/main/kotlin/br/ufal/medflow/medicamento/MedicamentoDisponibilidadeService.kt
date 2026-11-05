package br.ufal.medflow.medicamento

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.ResumoDisponibilidadeResponse
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MedicamentoDisponibilidadeService(
    private val medicamentoRepository: MedicamentoRepository,
    private val ubsRepository: UbsRepository,
    private val itemEstoqueRepository: ItemEstoqueRepository,
) {

    fun obterResumo(medicamentoId: Long): ResumoDisponibilidadeResponse {
        if (!medicamentoRepository.existsById(medicamentoId)) {
            throw RecursoNaoEncontradoException("Medicamento", medicamentoId)
        }

        return ResumoDisponibilidadeResponse(
            totalUnidades = ubsRepository.count(),
            unidadesComItem = itemEstoqueRepository.contarUnidadesComItem(medicamentoId),
        )
    }
}
