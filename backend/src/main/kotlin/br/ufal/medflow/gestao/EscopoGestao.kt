package br.ufal.medflow.gestao

import br.ufal.medflow.auth.CredenciaisInvalidasException
import br.ufal.medflow.auth.UsuarioRepository
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Recorte dos dados que o gestor pode ver: uma UBS ou a rede toda (`ubsId` nulo). */
data class EscopoGestao(val ubsId: Long?)

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
     * Com vínculo, o escopo é sempre a UBS vinculada e pedir outra é proibido. Sem vínculo, vale o `ubsId` pedido
     * (que precisa existir) ou a rede toda.
     */
    fun resolver(usuarioId: Long, ubsIdPedida: Long?): EscopoGestao {
        // token de conta apagada não vale, nem para ver a rede
        if (!usuarios.existsById(usuarioId)) throw CredenciaisInvalidasException()

        val vinculo = usuarios.findUbsGestaoId(usuarioId)
        if (vinculo != null) {
            if (ubsIdPedida != null && ubsIdPedida != vinculo) throw AcessoNegadoGestaoException(ubsIdPedida)
            return EscopoGestao(vinculo)
        }
        if (ubsIdPedida != null && !ubsRepository.existsById(ubsIdPedida)) throw RecursoNaoEncontradoException("UBS", ubsIdPedida)
        return EscopoGestao(ubsIdPedida)
    }

    fun descrever(usuarioId: Long, ubsIdPedida: Long?): EscopoResponse {
        val escopo = resolver(usuarioId, ubsIdPedida)
        val ubs = escopo.ubsId?.let { id ->
            val encontrada = ubsRepository.findByIdOrNull(id) ?: throw RecursoNaoEncontradoException("UBS", id)
            UbsEscopoResponse(id, encontrada.nome, encontrada.cnes)
        }
        return EscopoResponse(ubs, restrito = usuarios.findUbsGestaoId(usuarioId) != null)
    }
}
