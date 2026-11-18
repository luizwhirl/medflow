package br.ufal.medflow.estoque

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
}
