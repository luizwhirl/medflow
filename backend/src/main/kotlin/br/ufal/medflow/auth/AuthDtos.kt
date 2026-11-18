package br.ufal.medflow.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val senha: String,
)

/** O papel não vem do cliente: toda conta criada por aqui é CIDADAO. Campos a mais no corpo são ignorados. */
data class CadastroRequest(
    @field:NotBlank @field:Size(max = 120) val nome: String,
    @field:NotBlank @field:Email @field:Size(max = 160) val email: String,
    @field:NotBlank @field:Size(min = 8, max = 72) val senha: String,
)

data class TokenResponse(
    val token: String,
    val tipo: String = "Bearer",
    val expiraEmSegundos: Long,
    val papel: PapelUsuario,
)

data class UsuarioResponse(val id: Long, val nome: String, val email: String, val papel: PapelUsuario)

fun Usuario.toResponse() = UsuarioResponse(id = requireNotNull(id), nome = nome, email = email, papel = papel)
