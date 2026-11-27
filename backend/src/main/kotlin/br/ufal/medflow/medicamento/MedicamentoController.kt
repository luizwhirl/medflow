package br.ufal.medflow.medicamento

import br.ufal.medflow.common.PaginaResponse
import br.ufal.medflow.common.Paginacao
import br.ufal.medflow.common.ParametroInvalidoException
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.common.toPaginaResponse
import br.ufal.medflow.estoque.DisponibilidadeResponse
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.ResumoDisponibilidadeResponse
import br.ufal.medflow.estoque.toDisponibilidadeResponse
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.MultiValueMap
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

    private companion object {
        const val MAX_ALTERNATIVAS = 3
        const val MAX_TAMANHO_BUSCA = 200
    }

    /** Lista medicamentos com paginação, busca tolerante e filtro por categoria. */
    @GetMapping
    @Parameter(
        name = "busca",
        `in` = ParameterIn.QUERY,
        description = "Até três termos ou alternativas de reconhecimento de voz.",
        array = ArraySchema(
            schema = Schema(type = "string", maxLength = MAX_TAMANHO_BUSCA),
            maxItems = MAX_ALTERNATIVAS,
        ),
    )
    fun listar(
        @Parameter(hidden = true)
        @RequestParam parametros: MultiValueMap<String, String>,
        @RequestParam(required = false) categoria: String?,
        @Parameter(description = "Página, a partir de 0.")
        @RequestParam(defaultValue = "0") @Min(0) pagina: Int,
        @Parameter(description = "Itens por página, de 1 a ${Paginacao.TAMANHO_MAXIMO}.")
        @RequestParam(defaultValue = "${Paginacao.TAMANHO_PADRAO}") @Min(1) @Max(Paginacao.TAMANHO_MAXIMO.toLong())
        tamanho: Int,
    ): PaginaResponse<MedicamentoResponse> {
        // Preserva vírgulas dentro de uma transcrição.
        val alternativas = parametros["busca"].orEmpty()

        if (
            alternativas.size > MAX_ALTERNATIVAS ||
            alternativas.any { it.length > MAX_TAMANHO_BUSCA }
        ) {
            throw ParametroInvalidoException("busca")
        }

        val termos = alternativas
            .map { it.replace(Regex("[\\r\\n]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val categoriaFiltrada = categoria?.trim().orEmpty()

        // Nas consultas nativas, a ordenação já está definida no SQL.
        val paginaSemOrdem = PageRequest.of(pagina, tamanho)

        val resultado = when {
            termos.isNotEmpty() ->
                medicamentoRepository.buscarPorRelevancia(
                    termos.joinToString("\n"),
                    categoriaFiltrada,
                    paginaSemOrdem,
                )

            categoriaFiltrada.isNotEmpty() ->
                medicamentoRepository.buscarPorCategoria(
                    categoriaFiltrada,
                    paginaSemOrdem,
                )

            else ->
                medicamentoRepository.findAll(
                    Paginacao.de(pagina, tamanho, "nome"),
                )
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
