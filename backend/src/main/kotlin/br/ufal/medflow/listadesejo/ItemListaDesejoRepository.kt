package br.ufal.medflow.listadesejo

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Acesso aos itens da lista de desejos, sempre filtrados pelo aparelho dono. */
interface ItemListaDesejoRepository : JpaRepository<ItemListaDesejo, Long> {

    @EntityGraph(attributePaths = ["ubs", "medicamento"])
    fun findAllByDispositivoIdOrderByCriadoEmDescIdDesc(
        dispositivoId: UUID,
    ): List<ItemListaDesejo>

    @EntityGraph(attributePaths = ["ubs", "medicamento"])
    fun findByIdAndDispositivoId(
        id: Long,
        dispositivoId: UUID,
    ): ItemListaDesejo?

    fun existsByDispositivoIdAndUbsIdAndMedicamentoId(
        dispositivoId: UUID,
        ubsId: Long,
        medicamentoId: Long,
    ): Boolean
}
