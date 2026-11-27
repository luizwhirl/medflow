package br.ufal.medflow.listadesejo

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.SQLException
import java.util.UUID

/** Indica que o medicamento já foi adicionado à lista para a mesma UBS. */
class ItemListaDesejoDuplicadoException :
    RuntimeException("Medicamento já cadastrado na lista de desejos para esta UBS")

/** Regras de negócio da lista de desejos de um aparelho (sem login). */
@Service
@Transactional(readOnly = true)
class ItemListaDesejoService(
    private val itemListaDesejoRepository: ItemListaDesejoRepository,
    private val ubsRepository: UbsRepository,
    private val medicamentoRepository: MedicamentoRepository,
) {

    fun listar(dispositivoId: UUID): List<ItemListaDesejoResponse> =
        itemListaDesejoRepository
            .findAllByDispositivoIdOrderByCriadoEmDescIdDesc(dispositivoId)
            .map { it.toResponse() }

    @Transactional
    fun criar(
        dispositivoId: UUID,
        request: CriarItemListaDesejoRequest,
    ): ItemListaDesejoResponse {
        val ubs = ubsRepository.findByIdOrNull(request.ubsId)
            ?: throw RecursoNaoEncontradoException("UBS", request.ubsId)

        val medicamento = medicamentoRepository.findByIdOrNull(request.medicamentoId)
            ?: throw RecursoNaoEncontradoException("Medicamento", request.medicamentoId)

        if (
            itemListaDesejoRepository.existsByDispositivoIdAndUbsIdAndMedicamentoId(
                dispositivoId,
                request.ubsId,
                request.medicamentoId,
            )
        ) {
            throw ItemListaDesejoDuplicadoException()
        }

        val item = ItemListaDesejo(
            dispositivoId = dispositivoId,
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
        dispositivoId: UUID,
        itemId: Long,
        notificar: Boolean,
    ): ItemListaDesejoResponse {
        val item = buscarDoAparelho(dispositivoId, itemId)
        item.notificar = notificar
        return item.toResponse()
    }

    @Transactional
    fun remover(
        dispositivoId: UUID,
        itemId: Long,
    ) {
        itemListaDesejoRepository.delete(buscarDoAparelho(dispositivoId, itemId))
    }

    /** Item de outro aparelho responde como inexistente (404), para não revelar que ele existe. */
    private fun buscarDoAparelho(dispositivoId: UUID, itemId: Long): ItemListaDesejo =
        itemListaDesejoRepository.findByIdAndDispositivoId(itemId, dispositivoId)
            ?: throw RecursoNaoEncontradoException("Item da lista de desejos", itemId)

    /** SQLState 23505 indica violação de unicidade no PostgreSQL. */
    private fun Throwable.violaUnicidade(): Boolean =
        generateSequence(this) { it.cause }
            .filterIsInstance<SQLException>()
            .any { it.sqlState == UNIQUE_VIOLATION }

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
