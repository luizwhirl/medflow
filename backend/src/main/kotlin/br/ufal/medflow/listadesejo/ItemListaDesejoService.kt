package br.ufal.medflow.listadesejo

import br.ufal.medflow.auth.UsuarioRepository
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.UbsRepository
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Indica que o medicamento já foi adicionado à lista para a mesma UBS. */
class ItemListaDesejoDuplicadoException :
    RuntimeException("Medicamento já cadastrado na lista de desejos para esta UBS")

/** Regras de negócio da lista de desejos do usuário autenticado. */
@Service
@Transactional(readOnly = true)
class ItemListaDesejoService(
    private val itemListaDesejoRepository: ItemListaDesejoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val ubsRepository: UbsRepository,
    private val medicamentoRepository: MedicamentoRepository,
) {

    fun listar(usuarioId: Long): List<ItemListaDesejoResponse> =
        itemListaDesejoRepository.findAllByUsuarioIdOrderByCriadoEmDescIdDesc(usuarioId)
            .map { it.toResponse() }

    @Transactional
    fun criar(
        usuarioId: Long,
        request: CriarItemListaDesejoRequest,
    ): ItemListaDesejoResponse {
        val usuario = usuarioRepository.findByIdOrNull(usuarioId)
            ?: throw RecursoNaoEncontradoException("Usuário", usuarioId)

        val ubs = ubsRepository.findByIdOrNull(request.ubsId)
            ?: throw RecursoNaoEncontradoException("UBS", request.ubsId)

        val medicamento = medicamentoRepository.findByIdOrNull(request.medicamentoId)
            ?: throw RecursoNaoEncontradoException("Medicamento", request.medicamentoId)

        if (
            itemListaDesejoRepository.existsByUsuarioIdAndUbsIdAndMedicamentoId(
                usuarioId,
                request.ubsId,
                request.medicamentoId,
            )
        ) {
            throw ItemListaDesejoDuplicadoException()
        }

        val item = ItemListaDesejo(
            usuario = usuario,
            ubs = ubs,
            medicamento = medicamento,
            notificar = request.notificar,
        )

        try {
            return itemListaDesejoRepository.saveAndFlush(item).toResponse()
        } catch (ex: DataIntegrityViolationException) {
            val duplicado = generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<ConstraintViolationException>()
                .any { it.constraintName == "uk_item_lista_desejo" }

            if (duplicado) {
                throw ItemListaDesejoDuplicadoException()
            }

            throw ex
        }
    }

    @Transactional
    fun atualizarNotificacao(
        usuarioId: Long,
        itemId: Long,
        notificar: Boolean,
    ): ItemListaDesejoResponse {
        val item = itemListaDesejoRepository.findByIdAndUsuarioId(itemId, usuarioId)
            ?: throw RecursoNaoEncontradoException("Item da lista de desejos", itemId)

        item.notificar = notificar

        return item.toResponse()
    }

    @Transactional
    fun remover(
        usuarioId: Long,
        itemId: Long,
    ) {
        val item = itemListaDesejoRepository.findByIdAndUsuarioId(itemId, usuarioId)
            ?: throw RecursoNaoEncontradoException("Item da lista de desejos", itemId)

        itemListaDesejoRepository.delete(item)
    }
}
