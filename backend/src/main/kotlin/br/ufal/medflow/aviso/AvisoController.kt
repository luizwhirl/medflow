package br.ufal.medflow.aviso

import br.ufal.medflow.common.ParametroInvalidoException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** API de avisos sem login, identificados pelo UUID persistido no navegador. */
@RestController
@RequestMapping("/api/avisos")
class AvisoController(
    private val avisoService: AvisoService,
) {

    @GetMapping
    fun listar(
        @RequestHeader(value = CABECALHO, required = false) dispositivo: String?,
    ): List<AvisoResponse> = avisoService.listar(dispositivoId(dispositivo))

    @GetMapping("/nao-lidos/total")
    fun contarNaoLidos(
        @RequestHeader(value = CABECALHO, required = false) dispositivo: String?,
    ): AvisosNaoLidosResponse = avisoService.contarNaoLidos(dispositivoId(dispositivo))

    @PatchMapping("/lidas")
    fun marcarTodosComoLidos(
        @RequestHeader(value = CABECALHO, required = false) dispositivo: String?,
    ): AvisosLidosResponse = avisoService.marcarTodosComoLidos(dispositivoId(dispositivo))

    /** O UUID é um identificador do navegador, não uma autenticação forte. */
    private fun dispositivoId(valor: String?): UUID {
        if (valor == null || !UUID_CANONICO.matches(valor)) {
            throw ParametroInvalidoException(
                CABECALHO,
                "Envie o cabeçalho $CABECALHO com o identificador (UUID) deste aparelho",
            )
        }
        return UUID.fromString(valor)
    }

    private companion object {
        const val CABECALHO = "X-Dispositivo-Id"
        private val UUID_CANONICO =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
