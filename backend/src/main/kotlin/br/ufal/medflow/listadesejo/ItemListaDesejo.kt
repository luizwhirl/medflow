package br.ufal.medflow.listadesejo

import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.ubs.Ubs
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Medicamento que um cidadão deseja acompanhar em uma UBS.
 * Pertence a uma conta ([usuario]) ou a um aparelho sem login ([dispositivoId]), nunca aos dois.
 */
@Entity
@Table(name = "item_lista_desejo")
class ItemListaDesejo(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    var usuario: Usuario? = null,

    @Column(name = "dispositivo_id")
    var dispositivoId: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubs_id", nullable = false)
    var ubs: Ubs,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicamento_id", nullable = false)
    var medicamento: Medicamento,

    @Column(nullable = false)
    var notificar: Boolean = true,

    @Column(name = "criado_em", nullable = false)
    var criadoEm: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
