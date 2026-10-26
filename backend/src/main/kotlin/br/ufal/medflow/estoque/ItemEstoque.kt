package br.ufal.medflow.estoque

import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.ubs.Ubs
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

enum class StatusEstoque { DISPONIVEL, RESERVADO, EM_FALTA }

/** Posição de estoque de um medicamento em uma UBS. */
@Entity
@Table(name = "item_estoque")
class ItemEstoque(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubs_id")
    var ubs: Ubs,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicamento_id")
    var medicamento: Medicamento,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: StatusEstoque,

    @Column(nullable = false)
    var quantidade: Int = 0,

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
