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

    /** Retorna somente os avisos do cidadão, dos mais recentes aos mais antigos. */
    fun listar(usuarioId: Long): List<AvisoResponse> {
        validarUsuario(usuarioId)

        return avisoRepository
            .findAllByUsuarioIdOrderByCriadoEmDescIdDesc(usuarioId)
            .map { it.toResponse() }
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
