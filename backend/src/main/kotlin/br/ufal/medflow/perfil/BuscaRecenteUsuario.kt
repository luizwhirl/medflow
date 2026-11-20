package br.ufal.medflow.perfil

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable

/**
 * Identifica uma busca recente pela conta do cidadão
 * e pela posição no histórico.
 */
@Embeddable
data class BuscaRecenteUsuarioId(
    @Column(name = "usuario_id", nullable = false)
    var usuarioId: Long = 0L,

    @Column(name = "ordem", nullable = false)
    var ordem: Short = 0,
) : Serializable

/**
 * Busca recente armazenada no perfil do cidadão.
 *
 * A posição 0 representa o primeiro termo do histórico.
 * O banco permite no máximo as posições de 0 a 9.
 */
@Entity
@Table(name = "busca_recente_usuario")
class BuscaRecenteUsuario(
    @EmbeddedId
    var id: BuscaRecenteUsuarioId = BuscaRecenteUsuarioId(),

    @Column(nullable = false, length = 120)
    var termo: String = "",
)
