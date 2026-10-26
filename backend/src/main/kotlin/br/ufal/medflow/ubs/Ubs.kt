package br.ufal.medflow.ubs

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class TipoUnidade { UBS, USF }

/** Unidade de saúde, identificada oficialmente pelo código CNES. */
@Entity
@Table(name = "ubs")
class Ubs(
    @Column(nullable = false, unique = true, length = 7)
    var cnes: String,

    @Column(nullable = false)
    var nome: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var tipo: TipoUnidade,

    @Column(nullable = false)
    var bairro: String,

    @Column(nullable = false)
    var endereco: String,

    var telefone: String? = null,

    var horario: String? = null,

    @Column(name = "codigo_ibge_municipio", nullable = false, length = 7)
    var codigoIbgeMunicipio: String,

    @Column(nullable = false)
    var latitude: Double,

    @Column(nullable = false)
    var longitude: Double,

    /** Última sincronização com a BNAFAR / e-SUS AF. */
    @Column(name = "ultima_sincronizacao")
    var ultimaSincronizacao: Instant? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
