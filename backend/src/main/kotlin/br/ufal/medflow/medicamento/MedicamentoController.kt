package br.ufal.medflow.medicamento

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.DisponibilidadeResponse
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.toDisponibilidadeResponse
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/medicamentos")
@Transactional(readOnly = true)
class MedicamentoController(
    private val medicamentoRepository: MedicamentoRepository,
    private val itemEstoqueRepository: ItemEstoqueRepository,
) {

    /** Lista medicamentos; `busca` filtra por nome ou categoria. */
    @GetMapping
    fun listar(@RequestParam(required = false) busca: String?): List<MedicamentoResponse> {
        val termo = busca?.trim().orEmpty()
        val resultado = if (termo.isEmpty()) medicamentoRepository.findAllByOrderByNomeAsc()
        else medicamentoRepository.buscar(termo)
        return resultado.map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun buscar(@PathVariable id: Long): MedicamentoResponse =
        (medicamentoRepository.findByIdOrNull(id) ?: throw RecursoNaoEncontradoException("Medicamento", id))
            .toResponse()

    /** Em quais UBS o medicamento está disponível, reservado ou em falta. */
    @GetMapping("/{id}/disponibilidade")
    fun disponibilidade(@PathVariable id: Long): List<DisponibilidadeResponse> {
        if (!medicamentoRepository.existsById(id)) throw RecursoNaoEncontradoException("Medicamento", id)
        return itemEstoqueRepository.findByMedicamento(id).map { it.toDisponibilidadeResponse() }
    }
}
