package br.ufal.medflow.perfil

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Consultas ao histórico de buscas recentes.
 *
 * Todas as operações são filtradas pelo ID do cidadão,
 * sem carregar históricos de outros usuários.
 */
interface BuscaRecenteUsuarioRepository :
    JpaRepository<BuscaRecenteUsuario, BuscaRecenteUsuarioId> {

    /** Retorna os termos na ordem armazenada no perfil. */
    @Query(
        """
        SELECT b
        FROM BuscaRecenteUsuario b
        WHERE b.id.usuarioId = :usuarioId
        ORDER BY b.id.ordem ASC
        """,
    )
    fun listarPorUsuario(
        @Param("usuarioId") usuarioId: Long,
    ): List<BuscaRecenteUsuario>

    /**
     * Remove o histórico de um único cidadão.
     *
     * Será utilizado quando o cidadão desativar
     * o armazenamento ou atualizar suas buscas.
     */
    @Modifying
    @Query(
        """
        DELETE FROM BuscaRecenteUsuario b
        WHERE b.id.usuarioId = :usuarioId
        """,
    )
    fun removerPorUsuario(
        @Param("usuarioId") usuarioId: Long,
    ): Int
}
