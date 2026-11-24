package br.ufal.medflow.listadesejo

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

/** Medicamento que um aparelho (sem login) deseja acompanhar em uma UBS. */
@Entity
@Table(name = "item_lista_desejo")
class ItemListaDesejo(
    /** UUID gerado no navegador e enviado no cabeçalho X-Dispositivo-Id. */
    @Column(name = "dispositivo_id", nullable = false)
    var dispositivoId: UUID,

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
