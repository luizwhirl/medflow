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

    @Query(
        """
        select m from Medicamento m
        where lower(m.categoria) = lower(:categoria)
        order by m.nome
        """,
    )
    fun buscarPorCategoria(
        @Param("categoria") categoria: String,
    ): List<Medicamento>

    @Query(
        """
        select m from Medicamento m
        where (
            lower(m.nome) like lower(concat('%', :termo, '%'))
            or lower(m.categoria) like lower(concat('%', :termo, '%'))
        )
        and lower(m.categoria) = lower(:categoria)
        order by m.nome
        """,
    )
    fun buscarPorCategoriaETermo(
        @Param("termo") termo: String,
        @Param("categoria") categoria: String,
    ): List<Medicamento>

    @Query(
        """
        select distinct m.categoria from Medicamento m
        where m.categoria is not null
        order by m.categoria
        """,
    )
    fun listarCategorias(): List<String>

    fun findAllByOrderByNomeAsc(): List<Medicamento>
}
