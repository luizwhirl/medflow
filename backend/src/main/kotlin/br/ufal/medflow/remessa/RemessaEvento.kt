package br.ufal.medflow.remessa

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

/** Etapas na ordem em que acontecem. */
enum class EtapaRemessa { PEDIDO, SEPARADO_CAF, EM_TRANSPORTE, RECEBIDO, DISPONIVEL }

/** Momento em que uma remessa chegou a uma etapa. */
@Entity
@Table(name = "remessa_evento")
class RemessaEvento(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "remessa_id")
    var remessa: Remessa,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var etapa: EtapaRemessa,

    @Column(name = "ocorrido_em", nullable = false)
    var ocorridoEm: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
