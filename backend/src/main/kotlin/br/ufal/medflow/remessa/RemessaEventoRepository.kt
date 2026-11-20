package br.ufal.medflow.remessa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RemessaEventoRepository : JpaRepository<RemessaEvento, Long> {

    /** Eventos das remessas informadas, do mais antigo para o mais recente. */
    @Query("select e from RemessaEvento e join fetch e.remessa where e.remessa.id in :remessaIds order by e.ocorridoEm, e.id")
    fun findDasRemessas(@Param("remessaIds") remessaIds: Collection<Long>): List<RemessaEvento>
}
