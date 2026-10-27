package br.ufal.medflow.estoque

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

/** Lote de uma posição de estoque, com validade própria. */
@Entity
@Table(name = "lote_estoque")
class LoteEstoque(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_estoque_id")
    var itemEstoque: ItemEstoque,

    @Column(name = "numero_lote", nullable = false, length = 40)
    var numeroLote: String,

    @Column(nullable = false)
    var validade: LocalDate,

    @Column(nullable = false)
    var quantidade: Int = 0,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
