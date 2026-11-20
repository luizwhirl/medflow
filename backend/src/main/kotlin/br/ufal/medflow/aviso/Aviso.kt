package br.ufal.medflow.aviso

import br.ufal.medflow.auth.Usuario
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/** Tipos de avisos enviados aos cidadãos. */
enum class TipoAviso {
    MEDICAMENTO_DISPONIVEL,
    UBS_DADOS_ATRASADOS,
}

/** Aviso pertencente a um cidadão autenticado. */
@Entity
@Table(name = "aviso")
class Aviso(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    var usuario: Usuario,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    var tipo: TipoAviso,

    @Column(nullable = false, length = 160)
    var titulo: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var texto: String,

    @Column(nullable = false)
    var lido: Boolean = false,

    @Column(name = "criado_em", nullable = false)
    var criadoEm: Instant = Instant.now(),

    @Column(name = "chave_evento", nullable = false, length = 200)
    var chaveEvento: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
