package br.ufal.medflow.estoque

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface LoteEstoqueRepository : JpaRepository<LoteEstoque, Long> {

    /** Lotes com saldo e validade até a data informada (inclui os já vencidos), do que vence primeiro. */
    @Query(
        """
        select l from LoteEstoque l
        join fetch l.itemEstoque i join fetch i.ubs join fetch i.medicamento
        where l.validade <= :limite and l.quantidade > 0
        order by l.validade
        """,
    )
    fun findComValidadeAte(@Param("limite") limite: LocalDate): List<LoteEstoque>
}
