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

    /** Lista medicamentos; `busca` e `categoria` podem ser combinados. */
    @GetMapping
    fun listar(
        @RequestParam(required = false) busca: String?,
        @RequestParam(required = false) categoria: String?,
    ): List<MedicamentoResponse> {
        val termo = busca?.trim().orEmpty()
        val categoriaFiltrada = categoria?.trim().orEmpty()

        val resultado = when {
            termo.isNotEmpty() && categoriaFiltrada.isNotEmpty() ->
                medicamentoRepository.buscarPorCategoriaETermo(
                    termo,
                    categoriaFiltrada,
                )

            categoriaFiltrada.isNotEmpty() ->
                medicamentoRepository.buscarPorCategoria(categoriaFiltrada)

            termo.isNotEmpty() ->
                medicamentoRepository.buscar(termo)

            else ->
                medicamentoRepository.findAllByOrderByNomeAsc()
        }

        return resultado.map { it.toResponse() }
    }

    /** Lista as categorias distintas dos medicamentos cadastrados. */
    @GetMapping("/categorias")
    fun listarCategorias(): List<String> =
        medicamentoRepository.listarCategorias()

    @GetMapping("/{id}")
    fun buscar(@PathVariable id: Long): MedicamentoResponse =
        (medicamentoRepository.findByIdOrNull(id)
            ?: throw RecursoNaoEncontradoException("Medicamento", id))
            .toResponse()

    /** Em quais UBS o medicamento está disponível, reservado ou em falta. */
    @GetMapping("/{id}/disponibilidade")
    fun disponibilidade(@PathVariable id: Long): List<DisponibilidadeResponse> {
        if (!medicamentoRepository.existsById(id)) {
            throw RecursoNaoEncontradoException("Medicamento", id)
        }

        return itemEstoqueRepository.findByMedicamento(id)
            .map { it.toDisponibilidadeResponse() }
    }
}
