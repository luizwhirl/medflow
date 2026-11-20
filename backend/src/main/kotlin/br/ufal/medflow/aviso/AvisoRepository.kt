package br.ufal.medflow.aviso

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/** Acesso aos avisos, sempre restrito ao usuário autenticado quando aplicável. */
interface AvisoRepository : JpaRepository<Aviso, Long> {

    /** Lista os avisos do cidadão, começando pelos mais recentes. */
    fun findAllByUsuarioIdOrderByCriadoEmDescIdDesc(
        usuarioId: Long,
    ): List<Aviso>

    /** Marca todos os avisos não lidos do cidadão em uma única operação no banco. */
    @Modifying
    @Query(
        """
        update Aviso a
        set a.lido = true
        where a.usuario.id = :usuarioId
          and a.lido = false
        """,
    )
    fun marcarTodosComoLidos(
        @Param("usuarioId") usuarioId: Long,
    ): Int

    /**
     * Insere avisos de dados atrasados para cidadãos que acompanham
     * a UBS e possuem notificações habilitadas.
     *
     * SELECT DISTINCT evita múltiplos avisos para o mesmo cidadão.
     * ON CONFLICT impede a duplicação de um evento já registrado.
     *
     * A conversão explícita de criadoEm para TIMESTAMPTZ evita
     * incompatibilidade de tipos na consulta nativa do PostgreSQL.
     *
     * Deve ser executado dentro de uma transação de serviço.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO aviso (
                usuario_id,
                tipo,
                titulo,
                texto,
                lido,
                criado_em,
                chave_evento
            )
            SELECT DISTINCT
                i.usuario_id,
                'UBS_DADOS_ATRASADOS',
                :titulo,
                :texto,
                FALSE,
                CAST(:criadoEm AS TIMESTAMP WITH TIME ZONE),
                :chaveEvento
            FROM item_lista_desejo i
            WHERE i.ubs_id = :ubsId
              AND i.notificar = TRUE
            ON CONFLICT ON CONSTRAINT uk_aviso_usuario_evento
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
