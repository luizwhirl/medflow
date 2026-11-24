package br.ufal.medflow.listadesejo

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    /** Dispositivos que desejam receber avisos de disponibilidade. */
    @Query(
        """
        select distinct i.dispositivoId
        from ItemListaDesejo i
        where i.ubs.id = :ubsId
          and i.medicamento.id = :medicamentoId
          and i.notificar = true
        """,
    )
    fun buscarDispositivosParaDisponibilidade(
        @Param("ubsId") ubsId: Long,
        @Param("medicamentoId") medicamentoId: Long,
    ): List<UUID>
}
