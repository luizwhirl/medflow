package br.ufal.medflow.estoque

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

enum class TipoMovimentacao { ENTRADA, DISPENSACAO, AJUSTE }

/** Entrada, saída ou correção de uma posição de estoque. A quantidade tem sinal: saída é negativa. */
@Entity
@Table(name = "movimentacao_estoque")
class MovimentacaoEstoque(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_estoque_id")
    var itemEstoque: ItemEstoque,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    var tipo: TipoMovimentacao,

    @Column(nullable = false)
    var quantidade: Int,

    @Column(name = "ocorrido_em", nullable = false)
    var ocorridoEm: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
