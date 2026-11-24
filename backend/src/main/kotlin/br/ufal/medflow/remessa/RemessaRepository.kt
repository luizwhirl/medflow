package br.ufal.medflow.remessa

import org.springframework.data.domain.Pageable
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

    /** Remessas do medicamento na UBS ainda sem a etapa DISPONIVEL, da que chega primeiro (use uma página de 1). */
    @Query(
        """
        select r from Remessa r
        where r.ubs.id = :ubsId and r.medicamento.id = :medicamentoId
          and not exists (select 1 from RemessaEvento e where e.remessa = r and e.etapa = :concluida)
        order by r.previsaoChegada, r.id
        """,
    )
    fun findEmAndamentoDoMedicamento(
        @Param("ubsId") ubsId: Long,
        @Param("medicamentoId") medicamentoId: Long,
        pagina: Pageable,
        @Param("concluida") concluida: EtapaRemessa = EtapaRemessa.DISPONIVEL,
    ): List<Remessa>

    /** Remessas do medicamento na UBS, da mais recente para a mais antiga (use uma página de 1). */
    @Query(
        """
        select r from Remessa r
        where r.ubs.id = :ubsId and r.medicamento.id = :medicamentoId
        order by r.criadoEm desc, r.id desc
        """,
    )
    fun findMaisRecenteDoMedicamento(
        @Param("ubsId") ubsId: Long,
        @Param("medicamentoId") medicamentoId: Long,
        pagina: Pageable,
    ): List<Remessa>
}
