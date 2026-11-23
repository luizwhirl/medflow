package br.ufal.medflow.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class PapelUsuario { CIDADAO, GESTOR }

/** Conta de acesso. O e-mail é sempre guardado em minúsculas e a senha só como hash BCrypt. */
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
)
