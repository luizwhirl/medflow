package br.ufal.medflow.remessa

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.Positive
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class RastreamentoController(private val rastreamentoService: RastreamentoService) {

    @Operation(
        summary = "Rastreamento da remessa de um medicamento para uma UBS",
        description = "Etapa atual, previsão de chegada e linha do tempo com as cinco etapas. " +
            "Se houver mais de uma remessa, traz a em andamento que chega primeiro. " +
            "`remessa` é nula quando não há remessa para a UBS.",
    )
    @GetMapping("/api/medicamentos/{id}/rastreamento")
    fun rastrear(
        @PathVariable id: Long,
        @Parameter(description = "UBS cuja remessa será rastreada.")
        @RequestParam @Positive ubsId: Long,
    ): RastreamentoResponse = rastreamentoService.rastrear(id, ubsId)
}
