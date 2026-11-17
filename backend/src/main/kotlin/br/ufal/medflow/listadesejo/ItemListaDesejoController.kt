package br.ufal.medflow.listadesejo

import br.ufal.medflow.common.ParametroInvalidoException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** API da lista de desejos do usuário autenticado. */
@RestController
@RequestMapping("/api/lista-desejos")
class ItemListaDesejoController(
    private val itemListaDesejoService: ItemListaDesejoService,
) {

    @GetMapping
    fun listar(
        @AuthenticationPrincipal jwt: Jwt,
    ): List<ItemListaDesejoResponse> =
        itemListaDesejoService.listar(usuarioId(jwt))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun criar(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CriarItemListaDesejoRequest,
    ): ItemListaDesejoResponse =
        itemListaDesejoService.criar(usuarioId(jwt), request)

    @PatchMapping("/{id}")
    fun atualizarNotificacao(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        @Valid @RequestBody request: AtualizarNotificacaoRequest,
    ): ItemListaDesejoResponse =
        itemListaDesejoService.atualizarNotificacao(
            usuarioId(jwt),
            id,
            request.notificar
                ?: throw ParametroInvalidoException("notificar", "Informe true ou false"),
        )

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remover(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ) {
        itemListaDesejoService.remover(usuarioId(jwt), id)
    }

    /** A claim sub do JWT emitido pela L6 contém o ID do usuário. */
    private fun usuarioId(jwt: Jwt): Long =
        jwt.subject?.toLongOrNull()
            ?: throw ParametroInvalidoException("sub", "Identificador do usuário inválido")
}
