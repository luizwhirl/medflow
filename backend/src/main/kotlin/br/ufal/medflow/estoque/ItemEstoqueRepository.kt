package br.ufal.medflow.estoque

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** Contagem de itens agrupados por status. */
data class ContagemEstoquePorStatus(
    val status: StatusEstoque,
    val total: Long,
)

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
        select i from ItemEstoque i join fetch i.medicamento m
        where i.ubs.id = :ubsId
          and i.status = :status
        order by m.nome
        """,
    )
    fun findByUbsEStatus(
        @Param("ubsId") ubsId: Long,
        @Param("status") status: StatusEstoque,
    ): List<ItemEstoque>

    @Query(
        """
        select new br.ufal.medflow.estoque.ContagemEstoquePorStatus(i.status, count(i))
        from ItemEstoque i
        where i.ubs.id = :ubsId
        group by i.status
        """,
    )
    fun contarPorStatusDaUbs(
        @Param("ubsId") ubsId: Long,
    ): List<ContagemEstoquePorStatus>

    /** Todos os itens das unidades informadas, com UBS e medicamento carregados (usado na sincronização). */
    @Query(
        """
        select i from ItemEstoque i join fetch i.ubs join fetch i.medicamento
        where i.ubs.id in :ubsIds
        """,
    )
    fun findDasUbs(@Param("ubsIds") ubsIds: Collection<Long>): List<ItemEstoque>

    @Query(
        """
        select i from ItemEstoque i join fetch i.ubs u
        where i.medicamento.id = :medicamentoId
        order by case i.status when 'DISPONIVEL' then 0 when 'RESERVADO' then 1 else 2 end, u.nome
        """,
    )
    fun findByMedicamento(@Param("medicamentoId") medicamentoId: Long): List<ItemEstoque>
}
