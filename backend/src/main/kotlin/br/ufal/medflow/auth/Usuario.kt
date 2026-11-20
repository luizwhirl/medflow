package br.ufal.medflow.auth

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

enum class PapelUsuario { CIDADAO, GESTOR }

/**
 * Conta de acesso.
 *
 * O e-mail é sempre guardado em minúsculas e a senha só como hash BCrypt.
 * Os campos opcionais de perfil são utilizados pela API do cidadão.
 */
@Entity
@Table(name = "usuario")
class Usuario(
    @Column(nullable = false, length = 120)
    var nome: String,

    @Column(nullable = false, unique = true, length = 160)
    var email: String,

    @Column(name = "senha_hash", nullable = false, length = 100)
    var senhaHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var papel: PapelUsuario,

    @Column(name = "criado_em", nullable = false)
    var criadoEm: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * UBS escolhida pelo cidadão como referência.
     *
     * É opcional e não altera os dados cadastrais da UBS.
     * O relacionamento é carregado somente quando necessário.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubs_referencia_id")
    var ubsReferencia: Ubs? = null,

    /** Bairro informado pelo cidadão. */
    @Column(length = 120)
    var bairro: String? = null,

    /** Localização aproximada informada pelo cidadão. */
    @Column(name = "latitude_aproximada")
    var latitudeAproximada: Double? = null,

    @Column(name = "longitude_aproximada")
    var longitudeAproximada: Double? = null,

    /** Preferência de acessibilidade da interface. */
    @Column(name = "letra_maior", nullable = false)
    var letraMaior: Boolean = false,

    /** Define se o cidadão deseja manter seu histórico de buscas. */
    @Column(name = "salvar_buscas_recentes", nullable = false)
    var salvarBuscasRecentes: Boolean = false,
)
