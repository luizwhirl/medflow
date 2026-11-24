package br.ufal.medflow.gestao

import br.ufal.medflow.auth.CredenciaisInvalidasException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.Positive
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Rotas do gestor (`/api/gestao`, só o papel GESTOR). */
@RestController
@RequestMapping("/api/gestao")
class GestaoController(private val escopoService: EscopoGestaoService) {

    @Operation(
        summary = "UBS que o gestor pode consultar",
        description = "Com vínculo, devolve a UBS do gestor e `restrito: true`; pedir outra UBS responde 403. " +
            "Sem vínculo, devolve a UBS pedida em `ubsId` ou `ubs: null` para a rede toda.",
    )
    @GetMapping("/escopo")
    fun escopo(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UBS a consultar. Gestor com vínculo só pode informar a própria.")
        @RequestParam(required = false) @Positive ubsId: Long?,
    ): EscopoResponse = escopoService.descrever(usuarioId(jwt), ubsId)

    private fun usuarioId(jwt: Jwt): Long = jwt.subject?.toLongOrNull() ?: throw CredenciaisInvalidasException()
}
