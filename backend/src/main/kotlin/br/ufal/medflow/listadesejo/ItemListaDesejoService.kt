package br.ufal.medflow.listadesejo

import br.ufal.medflow.auth.CredenciaisInvalidasException
import br.ufal.medflow.auth.UsuarioRepository
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.SQLException

/** Indica que o medicamento já foi adicionado à lista para a mesma UBS. */
class ItemListaDesejoDuplicadoException :
    RuntimeException("Medicamento já cadastrado na lista de desejos para esta UBS")

/** Regras de negócio da lista de desejos, de uma conta logada ou de um aparelho sem login. */
@Service
@Transactional(readOnly = true)
class ItemListaDesejoService(
    private val itemListaDesejoRepository: ItemListaDesejoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val ubsRepository: UbsRepository,
    private val medicamentoRepository: MedicamentoRepository,
) {

    fun listar(dono: DonoLista): List<ItemListaDesejoResponse> {
        val itens = when (dono) {
            is DonoLista.Conta -> {
                validarUsuario(dono.usuarioId)
                itemListaDesejoRepository.findAllByUsuarioIdOrderByCriadoEmDescIdDesc(dono.usuarioId)
            }
            is DonoLista.Aparelho ->
                itemListaDesejoRepository.findAllByDispositivoIdOrderByCriadoEmDescIdDesc(dono.dispositivoId)
        }

        return itens.map { it.toResponse() }
    }

    @Transactional
    fun criar(
        dono: DonoLista,
        request: CriarItemListaDesejoRequest,
    ): ItemListaDesejoResponse {
        val usuario = when (dono) {
            is DonoLista.Conta ->
                usuarioRepository.findByIdOrNull(dono.usuarioId) ?: throw CredenciaisInvalidasException()
            is DonoLista.Aparelho -> null
        }

        val ubs = ubsRepository.findByIdOrNull(request.ubsId)
            ?: throw RecursoNaoEncontradoException("UBS", request.ubsId)

        val medicamento = medicamentoRepository.findByIdOrNull(request.medicamentoId)
            ?: throw RecursoNaoEncontradoException("Medicamento", request.medicamentoId)

        val jaExiste = when (dono) {
            is DonoLista.Conta -> itemListaDesejoRepository.existsByUsuarioIdAndUbsIdAndMedicamentoId(
                dono.usuarioId,
                request.ubsId,
                request.medicamentoId,
            )
            is DonoLista.Aparelho -> itemListaDesejoRepository.existsByDispositivoIdAndUbsIdAndMedicamentoId(
                dono.dispositivoId,
                request.ubsId,
                request.medicamentoId,
            )
        }
        if (jaExiste) {
            throw ItemListaDesejoDuplicadoException()
        }

        val item = ItemListaDesejo(
            usuario = usuario,
            dispositivoId = (dono as? DonoLista.Aparelho)?.dispositivoId,
            ubs = ubs,
            medicamento = medicamento,
            notificar = request.notificar,
        )

        try {
            return itemListaDesejoRepository.saveAndFlush(item).toResponse()
        } catch (ex: DataIntegrityViolationException) {
            // Protege também contra duas requisições simultâneas que tentem criar o mesmo item.
            if (ex.violaUnicidade()) {
                throw ItemListaDesejoDuplicadoException()
            }

            throw ex
        }
    }

    @Transactional
    fun atualizarNotificacao(
        dono: DonoLista,
        itemId: Long,
        notificar: Boolean,
    ): ItemListaDesejoResponse {
        val item = buscarDoDono(dono, itemId)

        item.notificar = notificar

        return item.toResponse()
    }

    @Transactional
    fun remover(
        dono: DonoLista,
        itemId: Long,
    ) {
        itemListaDesejoRepository.delete(buscarDoDono(dono, itemId))
    }

    /** Item de outro dono responde como inexistente (404), para não revelar que ele existe. */
    private fun buscarDoDono(dono: DonoLista, itemId: Long): ItemListaDesejo {
        val item = when (dono) {
            is DonoLista.Conta -> {
                validarUsuario(dono.usuarioId)
                itemListaDesejoRepository.findByIdAndUsuarioId(itemId, dono.usuarioId)
            }
            is DonoLista.Aparelho -> itemListaDesejoRepository.findByIdAndDispositivoId(itemId, dono.dispositivoId)
        }

        return item ?: throw RecursoNaoEncontradoException("Item da lista de desejos", itemId)
    }

    /** Um token de uma conta excluída não deve continuar autorizando operações. */
    private fun validarUsuario(usuarioId: Long) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw CredenciaisInvalidasException()
        }
    }

    /** SQLState 23505 indica violação de unicidade no PostgreSQL. */
    private fun Throwable.violaUnicidade(): Boolean =
        generateSequence(this) { it.cause }
            .filterIsInstance<SQLException>()
            .any { it.sqlState == UNIQUE_VIOLATION }

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
