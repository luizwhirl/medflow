package br.ufal.medflow.medicamento

import br.ufal.medflow.common.ParametroInvalidoException
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.ResumoDisponibilidadeResponse
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.integracao.CodigoIbge
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

    fun obterResumo(
        medicamentoId: Long,
        ibge: String? = null,
    ): ResumoDisponibilidadeResponse {
        val ibgeNormalizado = ibge?.let { codigo ->
            try {
                CodigoIbge.paraSeteDigitos(codigo)
            } catch (ex: IllegalArgumentException) {
                throw ParametroInvalidoException("ibge")
            }
        }

        if (!medicamentoRepository.existsById(medicamentoId)) {
            throw RecursoNaoEncontradoException("Medicamento", medicamentoId)
        }

        val totalUnidades = if (ibgeNormalizado == null) {
            ubsRepository.count()
        } else {
            ubsRepository.countByCodigoIbgeMunicipio(ibgeNormalizado)
        }

        val unidadesDisponiveis = if (ibgeNormalizado == null) {
            itemEstoqueRepository.contarUnidadesDisponiveis(
                medicamentoId,
                StatusEstoque.DISPONIVEL,
            )
        } else {
            itemEstoqueRepository.contarUnidadesDisponiveisPorMunicipio(
                medicamentoId,
                ibgeNormalizado,
                StatusEstoque.DISPONIVEL,
            )
        }

        return ResumoDisponibilidadeResponse(
            totalUnidades = totalUnidades,
            unidadesDisponiveis = unidadesDisponiveis,
        )
    }
}
