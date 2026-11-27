package br.ufal.medflow.estoque

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface MovimentacaoEstoqueRepository : JpaRepository<MovimentacaoEstoque, Long> {

    /** Movimentações da UBS a partir de uma data, da mais recente para a mais antiga. */
    @Query(
        """
        select m from MovimentacaoEstoque m
        join fetch m.itemEstoque i join fetch i.medicamento
        where i.ubs.id = :ubsId and m.ocorridoEm >= :desde
        order by m.ocorridoEm desc, m.id desc
        """,
    )
    fun findDaUbsDesde(@Param("ubsId") ubsId: Long, @Param("desde") desde: Instant): List<MovimentacaoEstoque>

    /** Últimas movimentações da UBS, da mais recente para a mais antiga. */
    @Query(
        """
        select m from MovimentacaoEstoque m
        join fetch m.itemEstoque i join fetch i.medicamento
        where i.ubs.id = :ubsId
        order by m.ocorridoEm desc, m.id desc
        """,
    )
    fun findUltimasDaUbs(@Param("ubsId") ubsId: Long, pagina: Pageable): List<MovimentacaoEstoque>

    /** Últimas movimentações de um medicamento na UBS, da mais recente para a mais antiga. */
    @Query(
        """
        select m from MovimentacaoEstoque m
        join fetch m.itemEstoque i join fetch i.medicamento
        where i.ubs.id = :ubsId and i.medicamento.id = :medicamentoId
        order by m.ocorridoEm desc, m.id desc
        """,
    )
    fun findUltimasDaUbsEMedicamento(
        @Param("ubsId") ubsId: Long,
        @Param("medicamentoId") medicamentoId: Long,
        pagina: Pageable,
    ): List<MovimentacaoEstoque>
}
