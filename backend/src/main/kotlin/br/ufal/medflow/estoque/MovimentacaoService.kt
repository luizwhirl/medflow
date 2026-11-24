package br.ufal.medflow.estoque

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class MovimentacaoResponse(
    val id: Long,
    val medicamentoId: Long,
    val medicamentoNome: String,
    val tipo: TipoMovimentacao,
    /** Com sinal: entrada positiva, dispensação negativa. */
    val quantidade: Int,
    val ocorridoEm: Instant,
)

fun MovimentacaoEstoque.toResponse() = MovimentacaoResponse(
    id = requireNotNull(id),
    medicamentoId = requireNotNull(itemEstoque.medicamento.id),
    medicamentoNome = itemEstoque.medicamento.nome,
    tipo = tipo,
    quantidade = quantidade,
    ocorridoEm = ocorridoEm,
)

@Service
@Transactional(readOnly = true)
class MovimentacaoService(
    private val movimentacaoRepository: MovimentacaoEstoqueRepository,
    private val ubsRepository: UbsRepository,
    private val medicamentoRepository: MedicamentoRepository,
) {

    /** Últimas movimentações da UBS, da mais recente para a mais antiga, com filtro opcional por medicamento. */
    fun ultimas(ubsId: Long, medicamentoId: Long?, limite: Int): List<MovimentacaoResponse> {
        if (!ubsRepository.existsById(ubsId)) throw RecursoNaoEncontradoException("UBS", ubsId)
        val pagina = PageRequest.of(0, limite)
        val movimentacoes = if (medicamentoId == null) {
            movimentacaoRepository.findUltimasDaUbs(ubsId, pagina)
        } else {
            if (!medicamentoRepository.existsById(medicamentoId)) throw RecursoNaoEncontradoException("Medicamento", medicamentoId)
            movimentacaoRepository.findUltimasDaUbsEMedicamento(ubsId, medicamentoId, pagina)
        }
        return movimentacoes.map { it.toResponse() }
    }
}
