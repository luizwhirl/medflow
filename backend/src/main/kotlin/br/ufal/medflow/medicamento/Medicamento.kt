package br.ufal.medflow.medicamento

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Medicamento, identificado oficialmente pelo código CATMAT. */
@Entity
@Table(name = "medicamento")
class Medicamento(
    @Column(nullable = false, unique = true, length = 20)
    var catmat: String,

    @Column(nullable = false)
    var nome: String,

    @Column(nullable = false)
    var concentracao: String,

    @Column(name = "forma_farmaceutica", nullable = false)
    var formaFarmaceutica: String,

    @Column(nullable = false)
    var categoria: String,

    /** Item da RENAME (Relação Nacional de Medicamentos Essenciais). */
    @Column(nullable = false)
    var rename: Boolean = true,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
