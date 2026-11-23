package br.ufal.medflow.aviso

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class TipoAviso {
    MEDICAMENTO_DISPONIVEL,
    UBS_DADOS_ATRASADOS,
}

/** Aviso vinculado exclusivamente ao UUID do dispositivo, sem conta de cidadão. */
@Entity
@Table(name = "aviso")
class Aviso(
    @Column(name = "dispositivo_id", nullable = false, columnDefinition = "uuid")
    var dispositivoId: UUID,

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
