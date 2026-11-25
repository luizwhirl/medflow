package br.ufal.medflow.busca

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Corpo opcional de `POST /api/medicamentos/{id}/buscas`. */
data class RegistrarBuscaRequest(
    /** UBS de referência de quem procurou, se houver. */
    @field:Positive val ubsId: Long? = null,
)

@RestController
class ContagemBuscaController(private val contagemBuscaService: ContagemBuscaService) {

    @Operation(
        summary = "Conta uma busca do medicamento",
        description = "O app chama ao abrir um medicamento. Público e sem dado pessoal: só soma 1 na contagem do dia " +
            "do medicamento (e da UBS de referência, se vier). Alimenta o \"mais procurados\" do painel.",
    )
    @PostMapping("/api/medicamentos/{id}/buscas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun registrar(
        @PathVariable @Positive id: Long,
        @Valid @RequestBody(required = false) pedido: RegistrarBuscaRequest?,
    ) {
        contagemBuscaService.registrar(id, pedido?.ubsId)
    }
}
