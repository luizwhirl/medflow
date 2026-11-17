package br.ufal.medflow.listadesejo

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

/** Acesso aos itens da lista de desejos, sempre filtrados pelo proprietário. */
interface ItemListaDesejoRepository : JpaRepository<ItemListaDesejo, Long> {

    @EntityGraph(attributePaths = ["ubs", "medicamento"])
    fun findAllByUsuarioIdOrderByCriadoEmDescIdDesc(
        usuarioId: Long,
    ): List<ItemListaDesejo>

    @EntityGraph(attributePaths = ["ubs", "medicamento"])
    fun findByIdAndUsuarioId(
        id: Long,
        usuarioId: Long,
    ): ItemListaDesejo?

    fun existsByUsuarioIdAndUbsIdAndMedicamentoId(
        usuarioId: Long,
        ubsId: Long,
        medicamentoId: Long,
    ): Boolean
}
