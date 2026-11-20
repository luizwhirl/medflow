package br.ufal.medflow.estoque

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class MovimentacaoController(private val movimentacaoService: MovimentacaoService) {

    @Operation(
        summary = "Últimas movimentações de estoque da UBS",
        description = "Da mais recente para a mais antiga. A quantidade tem sinal: entrada positiva, dispensação negativa.",
    )
    @GetMapping("/api/ubs/{id}/movimentacoes")
    fun ultimas(
        @PathVariable id: Long,
        @Parameter(description = "Mostra só as movimentações desse medicamento.")
        @RequestParam(required = false) medicamentoId: Long?,
        @Parameter(description = "Quantas movimentações trazer (1 a 100). Sem ele, 20.")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limite: Int,
    ): List<MovimentacaoResponse> = movimentacaoService.ultimas(id, medicamentoId, limite)
}
