package br.ufal.medflow.remessa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RemessaRepository : JpaRepository<Remessa, Long> {

    /** Remessas da UBS que ainda não chegaram à etapa DISPONIVEL, da que chega primeiro. */
    @Query(
        """
        select r from Remessa r join fetch r.medicamento
        where r.ubs.id = :ubsId
          and not exists (select 1 from RemessaEvento e where e.remessa = r and e.etapa = :concluida)
        order by r.previsaoChegada, r.id
        """,
    )
    fun findEmAndamentoDaUbs(
        @Param("ubsId") ubsId: Long,
        @Param("concluida") concluida: EtapaRemessa = EtapaRemessa.DISPONIVEL,
    ): List<Remessa>
}
