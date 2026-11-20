package br.ufal.medflow.aviso

import br.ufal.medflow.auth.CredenciaisInvalidasException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** API de avisos do cidadão autenticado. */
@RestController
@RequestMapping("/api/avisos")
class AvisoController(
    private val avisoService: AvisoService,
) {

    /** Lista os 50 avisos mais recentes do cidadão. */
    @GetMapping
    fun listar(
        @AuthenticationPrincipal jwt: Jwt,
    ): List<AvisoResponse> =
        avisoService.listar(usuarioId(jwt))

    /** Retorna o total de avisos não lidos, independentemente do limite da listagem. */
    @GetMapping("/nao-lidos/total")
    fun contarNaoLidos(
        @AuthenticationPrincipal jwt: Jwt,
    ): AvisosNaoLidosResponse =
        avisoService.contarNaoLidos(usuarioId(jwt))

    /** Marca todos os avisos não lidos do cidadão como lidos. */
    @PatchMapping("/lidas")
    fun marcarTodosComoLidos(
        @AuthenticationPrincipal jwt: Jwt,
    ): AvisosLidosResponse =
        avisoService.marcarTodosComoLidos(usuarioId(jwt))

    /** A claim sub do JWT contém o ID do usuário autenticado. */
    private fun usuarioId(jwt: Jwt): Long =
        jwt.subject?.toLongOrNull()
            ?: throw CredenciaisInvalidasException()
}
