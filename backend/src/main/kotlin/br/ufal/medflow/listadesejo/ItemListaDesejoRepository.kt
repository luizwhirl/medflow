package br.ufal.medflow.listadesejo

import br.ufal.medflow.auth.Usuario
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** Acesso aos itens da lista de desejos, sempre filtrados pelo proprietário quando aplicável. */
interface ItemListaDesejoRepository : JpaRepository<ItemListaDesejo, Long> {

    @EntityGraph(attributePaths = ["ubs", "medicamento"])
    fun findAllByUsuarioIdOrderByCriadoEmDescIdDesc(
        usuarioId: Long,
    ): List<ItemListaDesejo>

    @EntityGraph(attributePaths = ["ubs", "medicamento"])
    fun findByIdAndUsuarioId(
        id: Long,
        usuarioId: Long,
    ): ItemListaDesejo?

    fun existsByUsuarioIdAndUbsIdAndMedicamentoId(
        usuarioId: Long,
        ubsId: Long,
        medicamentoId: Long,
    ): Boolean

    /**
     * Dispositivos sem login que acompanham a UBS e o medicamento.
     * Consulta nativa para compatibilidade com a migração do PR #33,
     * inclusive antes de o mapeamento do dispositivo ser integrado ao JPA.
     */
    @Query(
        value = """
            SELECT DISTINCT i.dispositivo_id
            FROM item_lista_desejo i
            WHERE i.ubs_id = :ubsId
              AND i.medicamento_id = :medicamentoId
              AND i.notificar = TRUE
              AND i.dispositivo_id IS NOT NULL
        """,
        nativeQuery = true,
    )
    fun buscarDispositivosParaDisponibilidade(
        @Param("ubsId") ubsId: Long,
        @Param("medicamentoId") medicamentoId: Long,
    ): List<UUID>

    /**
     * Cidadãos que solicitaram notificações para um medicamento
     * específico em uma UBS.
     *
     * O filtro é executado diretamente no banco.
     */
    @Query(
        """
        select distinct i.usuario
        from ItemListaDesejo i
        where i.ubs.id = :ubsId
          and i.medicamento.id = :medicamentoId
          and i.notificar = true
        """,
    )
    fun buscarUsuariosParaDisponibilidade(
        @Param("ubsId") ubsId: Long,
        @Param("medicamentoId") medicamentoId: Long,
    ): List<Usuario>
}
