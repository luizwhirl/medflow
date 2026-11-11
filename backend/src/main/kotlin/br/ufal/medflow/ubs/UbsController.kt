package br.ufal.medflow.ubs

import br.ufal.medflow.common.ParametroInvalidoException
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ItemEstoqueResponse
import br.ufal.medflow.estoque.ResumoEstoqueUbsResponse
import br.ufal.medflow.estoque.StatusEstoque
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ubs")
@Transactional(readOnly = true)
class UbsController(
    private val ubsRepository: UbsRepository,
    private val ubsEstoqueService: UbsEstoqueService,
    private val ubsProximidadeService: UbsProximidadeService,
) {

    @Operation(
        summary = "Lista as UBS",
        description = "Sem parâmetros, em ordem de nome. Com lat e lng, da mais próxima para a mais distante, " +
            "com a distância em km. Com medicamentoId, cada UBS que tem o item traz o status e a quantidade.",
    )
    @GetMapping
    fun listar(
        @Parameter(description = "Latitude do ponto de referência (-90 a 90). Vai sempre junto com lng.")
        @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") lat: Double?,
        @Parameter(description = "Longitude do ponto de referência (-180 a 180). Vai sempre junto com lat.")
        @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") lng: Double?,
        @Parameter(description = "Distância máxima em km, maior que zero. Exige lat e lng. Sem ele, não há limite.")
        @RequestParam(required = false) @DecimalMin(value = "0.0", inclusive = false) raioKm: Double?,
        @Parameter(description = "Traz o status e a quantidade desse medicamento em cada UBS que o tem. Exige lat e lng.")
        @RequestParam(required = false) medicamentoId: Long?,
    ): List<UbsProximaResponse> {
        if (lat == null && lng == null) {
            if (raioKm != null || medicamentoId != null) {
                throw ParametroInvalidoException("raioKm e medicamentoId exigem lat e lng")
            }
            return ubsRepository.findAllByOrderByNomeAsc().map { it.toProximaResponse() }
        }
        if (lat == null || lng == null) throw ParametroInvalidoException("Informe lat e lng juntos")
        return ubsProximidadeService.buscar(lat, lng, raioKm, medicamentoId)
    }

    @GetMapping("/{id}")
    fun buscar(@PathVariable id: Long): UbsResponse =
        (ubsRepository.findByIdOrNull(id) ?: throw RecursoNaoEncontradoException("UBS", id)).toResponse()

    @GetMapping("/{id}/estoque")
    fun estoque(
        @PathVariable id: Long,
        @RequestParam(required = false) status: StatusEstoque?,
    ): List<ItemEstoqueResponse> =
        ubsEstoqueService.listarItens(id, status)

    @GetMapping("/{id}/estoque/resumo")
    fun resumoEstoque(
        @PathVariable id: Long,
        @RequestParam(required = false) status: StatusEstoque?,
    ): ResumoEstoqueUbsResponse =
        ubsEstoqueService.obterResumo(id, status)
}
