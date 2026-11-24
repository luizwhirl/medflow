package br.ufal.medflow.gestao

import br.ufal.medflow.auth.CredenciaisInvalidasException
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.UsuarioRepository
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Recorte dos dados que o gestor pode ver: a UBS dele (ou a pedida) ou a rede toda (`ubs` nula).
 * `restrito` indica que o gestor tem vínculo com uma UBS.
 */
data class EscopoGestao(val ubs: Ubs?, val restrito: Boolean) {
    val ubsId: Long? get() = ubs?.id
}

/** Gestor com vínculo pediu os dados de outra UBS. */
class AcessoNegadoGestaoException(ubsIdPedida: Long) :
    RuntimeException("Seu acesso é só da UBS vinculada ao seu usuário; a UBS $ubsIdPedida não pode ser consultada")

data class UbsEscopoResponse(val id: Long, val nome: String, val cnes: String)

/** `ubs` é nula quando o escopo é a rede toda. `restrito` indica que o gestor tem vínculo com uma UBS. */
data class EscopoResponse(val ubs: UbsEscopoResponse?, val restrito: Boolean)

@Service
@Transactional(readOnly = true)
class EscopoGestaoService(
    private val usuarios: UsuarioRepository,
    private val ubsRepository: UbsRepository,
) {

    /**
     * Com vínculo, o escopo é sempre a UBS vinculada e pedir outra (existente ou não) é proibido. Sem vínculo, vale o
     * `ubsId` pedido (que precisa existir) ou a rede toda. Todo endpoint de `/api/gestao` deve chamar este método.
     */
    fun resolver(usuarioId: Long, ubsIdPedida: Long?): EscopoGestao {
        // token de conta apagada, ou que deixou de ser de gestor, não vale, nem para ver a rede
        if (!usuarios.existsByIdAndPapel(usuarioId, PapelUsuario.GESTOR)) throw CredenciaisInvalidasException()

        val vinculo = usuarios.findUbsGestaoId(usuarioId)
        if (vinculo != null) {
            if (ubsIdPedida != null && ubsIdPedida != vinculo) throw AcessoNegadoGestaoException(ubsIdPedida)
            return EscopoGestao(buscar(vinculo), restrito = true)
        }
        return EscopoGestao(ubsIdPedida?.let(::buscar), restrito = false)
    }

    fun descrever(usuarioId: Long, ubsIdPedida: Long?): EscopoResponse {
        val escopo = resolver(usuarioId, ubsIdPedida)
        return EscopoResponse(
            ubs = escopo.ubs?.let { UbsEscopoResponse(requireNotNull(it.id), it.nome, it.cnes) },
            restrito = escopo.restrito,
        )
    }

    private fun buscar(id: Long): Ubs = ubsRepository.findByIdOrNull(id) ?: throw RecursoNaoEncontradoException("UBS", id)
}
