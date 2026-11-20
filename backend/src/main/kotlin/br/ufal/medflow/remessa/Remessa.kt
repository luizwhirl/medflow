package br.ufal.medflow.remessa

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
import java.time.LocalDate

/** Remessa de um medicamento da CAF para uma UBS. */
@Entity
@Table(name = "remessa")
class Remessa(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubs_id")
    var ubs: Ubs,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicamento_id")
    var medicamento: Medicamento,

    @Column(nullable = false)
    var quantidade: Int,

    @Column(name = "previsao_chegada", nullable = false)
    var previsaoChegada: LocalDate,

    @Column(name = "criado_em", nullable = false)
    var criadoEm: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
