package br.ufal.medflow.medicamento

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

// A ordem vem do Pageable. Os filtros por lower(nome) e lower(categoria) usam os índices pg_trgm da V7.
interface MedicamentoRepository : JpaRepository<Medicamento, Long> {

    @Query(
        """
        select m from Medicamento m
        where lower(m.nome) like lower(concat('%', :termo, '%'))
           or lower(m.categoria) like lower(concat('%', :termo, '%'))
        """,
    )
    fun buscar(@Param("termo") termo: String, pagina: Pageable): Page<Medicamento>

    @Query(
        """
        select m from Medicamento m
        where lower(m.categoria) = lower(:categoria)
        """,
    )
    fun buscarPorCategoria(
        @Param("categoria") categoria: String,
        pagina: Pageable,
    ): Page<Medicamento>

    @Query(
        """
        select m from Medicamento m
        where (
            lower(m.nome) like lower(concat('%', :termo, '%'))
            or lower(m.categoria) like lower(concat('%', :termo, '%'))
        )
        and lower(m.categoria) = lower(:categoria)
        """,
    )
    fun buscarPorCategoriaETermo(
        @Param("termo") termo: String,
        @Param("categoria") categoria: String,
        pagina: Pageable,
    ): Page<Medicamento>

    @Query(
        """
        select distinct m.categoria from Medicamento m
        where m.categoria is not null
        order by m.categoria
        """,
    )
    fun listarCategorias(): List<String>

    fun findByCatmatIn(catmats: Collection<String>): List<Medicamento>
}
