package br.ufal.medflow.aviso

import br.ufal.medflow.common.ParametroInvalidoException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Avisos públicos (sem login): o cidadão informa a UBS de referência e os remédios que acompanha. */
@RestController
@RequestMapping("/api/avisos")
class AvisoController(private val avisoService: AvisoService) {

    @Operation(
        summary = "Avisos de uma UBS para os medicamentos acompanhados",
        description = "Medicamentos com estoque disponível na UBS, remessas em andamento (com etapa e previsão) " +
            "e sincronização atrasada da unidade, do mais recente para o mais antigo.",
    )
    @GetMapping
    fun listar(
        @Parameter(description = "UBS de referência do cidadão.")
        @RequestParam ubsId: Long,
        @Parameter(description = "Medicamentos acompanhados (até $MAX_MEDICAMENTOS), separados por vírgula.")
        @RequestParam(required = false) medicamentoIds: List<Long>?,
    ): List<AvisoResponse> {
        if (ubsId <= 0) throw ParametroInvalidoException("ubsId", "Informe o id positivo de uma UBS")
        val ids = medicamentoIds.orEmpty()
        if (ids.any { it <= 0 }) {
            throw ParametroInvalidoException("medicamentoIds", "Os ids de medicamento devem ser positivos")
        }
        if (ids.size > MAX_MEDICAMENTOS) {
            throw ParametroInvalidoException("medicamentoIds", "Informe no máximo $MAX_MEDICAMENTOS medicamentos")
        }
        return avisoService.listar(ubsId, ids.toSet())
    }

    companion object {
        const val MAX_MEDICAMENTOS = 50
    }
}
