package br.ufal.medflow.medicamento

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MedicamentoRepository : JpaRepository<Medicamento, Long> {

    @Query(
        """
        select m from Medicamento m
        where lower(m.nome) like lower(concat('%', :termo, '%'))
           or lower(m.categoria) like lower(concat('%', :termo, '%'))
        order by m.nome
        """,
    )
    fun buscar(@Param("termo") termo: String): List<Medicamento>

    fun findAllByOrderByNomeAsc(): List<Medicamento>
}
