package br.ufal.medflow.estoque

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ItemEstoqueRepository : JpaRepository<ItemEstoque, Long> {

    @Query(
        """
        select i from ItemEstoque i join fetch i.medicamento m
        where i.ubs.id = :ubsId
        order by m.nome
        """,
    )
    fun findByUbs(@Param("ubsId") ubsId: Long): List<ItemEstoque>

    @Query(
        """
        select i from ItemEstoque i join fetch i.ubs u
        where i.medicamento.id = :medicamentoId
        order by case i.status when 'DISPONIVEL' then 0 when 'RESERVADO' then 1 else 2 end, u.nome
        """,
    )
    fun findByMedicamento(@Param("medicamentoId") medicamentoId: Long): List<ItemEstoque>
}
