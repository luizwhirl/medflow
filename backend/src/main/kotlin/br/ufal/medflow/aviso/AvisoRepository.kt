package br.ufal.medflow.aviso

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/** Acesso a avisos por dispositivo, com filtros e agregações no PostgreSQL. */
interface AvisoRepository : JpaRepository<Aviso, Long> {
    fun findTop50ByDispositivoIdOrderByCriadoEmDescIdDesc(dispositivoId: UUID): List<Aviso>

    fun findAllByDispositivoIdOrderByCriadoEmDescIdDesc(dispositivoId: UUID): List<Aviso>

    fun countByDispositivoIdAndLidoFalse(dispositivoId: UUID): Long

    /** Evita acumular avisos não lidos da mesma UBS e medicamento por dispositivo. */
    @Query(
        """
        select distinct a.dispositivoId
        from Aviso a
        where a.dispositivoId in :dispositivoIds
          and a.tipo = :tipo
          and a.lido = false
          and a.chaveEvento like concat(:prefixoEvento, '%')
        """,
    )
    fun buscarDispositivosComDisponibilidadeNaoLida(
        @Param("dispositivoIds") dispositivoIds: Collection<UUID>,
        @Param("tipo") tipo: TipoAviso,
        @Param("prefixoEvento") prefixoEvento: String,
    ): List<UUID>

    @Modifying
    @Query(
        """
        update Aviso a
        set a.lido = true
        where a.dispositivoId = :dispositivoId
          and a.lido = false
        """,
    )
    fun marcarTodosComoLidos(@Param("dispositivoId") dispositivoId: UUID): Int

    /** SQL insere um único aviso por aparelho que acompanha a UBS, ignorando duplicatas. */
    @Modifying
    @Query(
        value = """
            INSERT INTO aviso (
                dispositivo_id, tipo, titulo, texto, lido, criado_em, chave_evento
            )
            SELECT DISTINCT
                i.dispositivo_id,
                'UBS_DADOS_ATRASADOS',
                :titulo,
                :texto,
                FALSE,
                CAST(:criadoEm AS TIMESTAMP WITH TIME ZONE),
                :chaveEvento
            FROM item_lista_desejo i
            WHERE i.ubs_id = :ubsId
              AND i.dispositivo_id IS NOT NULL
              AND i.notificar = TRUE
            ON CONFLICT ON CONSTRAINT uk_aviso_dispositivo_evento
            DO NOTHING
        """,
        nativeQuery = true,
    )
    fun inserirAvisosDeAtraso(
        @Param("ubsId") ubsId: Long,
        @Param("titulo") titulo: String,
        @Param("texto") texto: String,
        @Param("criadoEm") criadoEm: Instant,
        @Param("chaveEvento") chaveEvento: String,
    ): Int
}
