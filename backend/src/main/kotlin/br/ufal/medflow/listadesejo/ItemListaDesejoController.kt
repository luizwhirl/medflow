package br.ufal.medflow.listadesejo

import br.ufal.medflow.common.ParametroInvalidoException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** API da lista de desejos, sem login: o dono é o aparelho informado no cabeçalho [CABECALHO_DISPOSITIVO]. */
@RestController
@RequestMapping("/api/lista-desejos")
class ItemListaDesejoController(
    private val itemListaDesejoService: ItemListaDesejoService,
) {

    @GetMapping
    fun listar(
        @RequestHeader(CABECALHO_DISPOSITIVO, required = false) dispositivo: String?,
    ): List<ItemListaDesejoResponse> =
        itemListaDesejoService.listar(aparelho(dispositivo))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun criar(
        @RequestHeader(CABECALHO_DISPOSITIVO, required = false) dispositivo: String?,
        @Valid @RequestBody request: CriarItemListaDesejoRequest,
    ): ItemListaDesejoResponse =
        itemListaDesejoService.criar(aparelho(dispositivo), request)

    @PatchMapping("/{id}")
    fun atualizarNotificacao(
        @RequestHeader(CABECALHO_DISPOSITIVO, required = false) dispositivo: String?,
        @PathVariable id: Long,
        @Valid @RequestBody request: AtualizarNotificacaoRequest,
    ): ItemListaDesejoResponse =
        itemListaDesejoService.atualizarNotificacao(
            aparelho(dispositivo),
            id,
            // @NotNull valida a entrada, mas o DTO usa Boolean?;
            // esta checagem garante um Boolean não nulo para o serviço.
            request.notificar
                ?: throw ParametroInvalidoException("notificar", "Informe true ou false"),
        )

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remover(
        @RequestHeader(CABECALHO_DISPOSITIVO, required = false) dispositivo: String?,
        @PathVariable id: Long,
    ) {
        itemListaDesejoService.remover(aparelho(dispositivo), id)
    }

    /** O cabeçalho é obrigatório e precisa ser um UUID no formato canônico. */
    private fun aparelho(dispositivo: String?): UUID {
        if (dispositivo == null || !UUID_CANONICO.matches(dispositivo)) {
            throw ParametroInvalidoException(
                CABECALHO_DISPOSITIVO,
                "Envie o cabeçalho $CABECALHO_DISPOSITIVO com o identificador (UUID) deste aparelho",
            )
        }
        return UUID.fromString(dispositivo)
    }

    companion object {
        const val CABECALHO_DISPOSITIVO = "X-Dispositivo-Id"

        /** `UUID.fromString` aceita formas abreviadas ("1-1-1-1-1"); aqui só o formato de 36 caracteres. */
        private val UUID_CANONICO =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
