package br.ufal.medflow.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface UsuarioRepository : JpaRepository<Usuario, Long> {
    fun findByEmail(email: String): Usuario?

    fun existsByEmail(email: String): Boolean

    /** UBS à qual o gestor está vinculado; nulo se não houver vínculo (ou a conta não existir). */
    @Query(value = "select ubs_gestao_id from usuario where id = :id", nativeQuery = true)
    fun findUbsGestaoId(@Param("id") id: Long): Long?

    fun existsByIdAndPapel(id: Long, papel: PapelUsuario): Boolean

    /**
     * Vincula o gestor (pelo e-mail) à UBS (pelo CNES), mas só se ele ainda não tiver vínculo e a UBS existir.
     * Sem a checagem da UBS, um CNES errado gravaria `NULL` e o gestor passaria a ver a rede toda.
     * Devolve quantas contas mudaram.
     */
    @Transactional
    @Modifying
    @Query(
        value = "update usuario set ubs_gestao_id = (select id from ubs where cnes = :cnes) " +
            "where email = :email and papel = 'GESTOR' and ubs_gestao_id is null " +
            "and exists (select 1 from ubs where cnes = :cnes)",
        nativeQuery = true,
    )
    fun vincularGestor(@Param("email") email: String, @Param("cnes") cnes: String): Int
}
