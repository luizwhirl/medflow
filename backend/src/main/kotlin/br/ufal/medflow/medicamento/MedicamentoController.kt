package br.ufal.medflow.medicamento

import br.ufal.medflow.common.PaginaResponse
import br.ufal.medflow.common.Paginacao
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.common.toPaginaResponse
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import br.ufal.medflow.estoque.DisponibilidadeResponse
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.ResumoDisponibilidadeResponse
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
    private val medicamentoDisponibilidadeService: MedicamentoDisponibilidadeService,
) {

    /** Lista medicamentos em ordem de nome, paginado; `busca` e `categoria` podem ser combinados. */
    @GetMapping
    fun listar(
        @RequestParam(required = false) busca: String?,
        @RequestParam(required = false) categoria: String?,
        @Parameter(description = "Página, a partir de 0.")
        @RequestParam(defaultValue = "0") @Min(0) pagina: Int,
        @Parameter(description = "Itens por página, de 1 a ${Paginacao.TAMANHO_MAXIMO}.")
        @RequestParam(defaultValue = "${Paginacao.TAMANHO_PADRAO}") @Min(1) @Max(Paginacao.TAMANHO_MAXIMO.toLong())
        tamanho: Int,
    ): PaginaResponse<MedicamentoResponse> {
        val termo = busca?.trim().orEmpty()
        val categoriaFiltrada = categoria?.trim().orEmpty()
        val paginacao = Paginacao.de(pagina, tamanho, "nome")

        val resultado = when {
            termo.isNotEmpty() && categoriaFiltrada.isNotEmpty() ->
                medicamentoRepository.buscarPorCategoriaETermo(
                    termo,
                    categoriaFiltrada,
                    paginacao,
                )

            categoriaFiltrada.isNotEmpty() ->
                medicamentoRepository.buscarPorCategoria(categoriaFiltrada, paginacao)

            termo.isNotEmpty() ->
                medicamentoRepository.buscar(termo, paginacao)

            else ->
                medicamentoRepository.findAll(paginacao)
        }

        return resultado.toPaginaResponse { it.toResponse() }
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

    /** Resumo da disponibilidade do medicamento nas UBS. */
    @GetMapping("/{id}/disponibilidade/resumo")
    fun resumoDisponibilidade(
        @PathVariable id: Long,
        @RequestParam(required = false) ibge: String?,
    ): ResumoDisponibilidadeResponse =
        medicamentoDisponibilidadeService.obterResumo(id, ibge)
}
