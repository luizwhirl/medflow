package br.ufal.medflow.aviso

import br.ufal.medflow.auth.CredenciaisInvalidasException
import br.ufal.medflow.auth.UsuarioRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Regras de consulta e leitura dos avisos do cidadão autenticado. */
@Service
@Transactional(readOnly = true)
class AvisoService(
    private val avisoRepository: AvisoRepository,
    private val usuarioRepository: UsuarioRepository,
) {

    /** Retorna os 50 avisos mais recentes do cidadão autenticado. */
    fun listar(usuarioId: Long): List<AvisoResponse> {
        validarUsuario(usuarioId)

        return avisoRepository
            .findTop50ByUsuarioIdOrderByCriadoEmDescIdDesc(usuarioId)
            .map { it.toResponse() }
    }

    /** Conta todos os avisos não lidos do cidadão, sem carregar os registros. */
    fun contarNaoLidos(usuarioId: Long): AvisosNaoLidosResponse {
        validarUsuario(usuarioId)

        val quantidade = avisoRepository.countByUsuarioIdAndLidoFalse(
            usuarioId,
        )

        return AvisosNaoLidosResponse(naoLidos = quantidade)
    }

    /** Marca todos os avisos não lidos do cidadão em uma única atualização no banco. */
    @Transactional
    fun marcarTodosComoLidos(usuarioId: Long): AvisosLidosResponse {
        validarUsuario(usuarioId)

        val atualizados = avisoRepository.marcarTodosComoLidos(usuarioId)

        return AvisosLidosResponse(atualizados = atualizados)
    }

    /** Tokens de contas excluídas não devem continuar autorizando operações. */
    private fun validarUsuario(usuarioId: Long) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw CredenciaisInvalidasException()
        }
    }
}
