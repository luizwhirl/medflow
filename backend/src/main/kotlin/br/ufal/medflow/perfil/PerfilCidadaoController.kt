package br.ufal.medflow.perfil

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * API de perfil do cidadão autenticado.
 *
 * O usuário é identificado pela claim sub do JWT.
 * Não é permitido informar o ID do proprietário na requisição.
 */
@RestController
@RequestMapping("/api/me")
class PerfilCidadaoController(
    private val perfilService: PerfilCidadaoService,
) {

    /**
     * Retorna os dados e as preferências do cidadão autenticado.
     */
    @GetMapping
    fun consultar(
        @AuthenticationPrincipal jwt: Jwt,
    ): PerfilCidadaoResponse =
        perfilService.consultar(obterUsuarioId(jwt))

    /**
     * Atualiza os dados e as preferências do cidadão autenticado.
     *
     * A UBS de referência deve existir.
     * Os campos são validados antes da chamada ao serviço.
     */
    @PutMapping
    fun atualizar(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: AtualizarPerfilRequest,
    ): PerfilCidadaoResponse =
        perfilService.atualizar(
            obterUsuarioId(jwt),
            request,
        )

    /**
     * Extrai o ID do cidadão da claim sub.
     *
     * Rejeita tokens sem identificador ou com um ID inválido,
     * evitando erros de conversão e acesso indevido.
     */
    private fun obterUsuarioId(jwt: Jwt): Long {
        val usuarioId = jwt.subject?.toLongOrNull()

        if (usuarioId == null || usuarioId <= 0L) {
            throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Token sem identificador de usuário válido",
            )
        }

        return usuarioId
    }
}
