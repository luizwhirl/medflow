package br.ufal.medflow.auth

import br.ufal.medflow.common.ParametroInvalidoException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

@Service
class AuthService(
    private val usuarios: UsuarioRepository,
    private val senhas: PasswordEncoder,
    private val jwt: JwtService,
) {
    /** Hash de uma senha qualquer, com o mesmo custo dos reais: gasta o mesmo tempo quando o e-mail não existe. */
    private val hashFalso by lazy { gerarHash("senha-que-ninguem-usa") }

    @Transactional(readOnly = true)
    fun login(email: String, senha: String): TokenResponse {
        val usuario = usuarios.findByEmail(normalizar(email))
        val confere = senha.cabeNoBcrypt() && senhas.matches(senha, usuario?.senhaHash ?: hashFalso)
        if (usuario == null || !confere) throw CredenciaisInvalidasException()

        val emitido = jwt.emitir(usuario)
        return TokenResponse(token = emitido.token, expiraEmSegundos = emitido.expiraEmSegundos, papel = usuario.papel)
    }

    /** Toda conta criada aqui é CIDADAO. Gestor só é criado direto no banco. */
    @Transactional
    fun cadastrar(pedido: CadastroRequest): UsuarioResponse {
        if (!pedido.senha.cabeNoBcrypt()) {
            throw ParametroInvalidoException("senha") // passa de $LIMITE_BCRYPT_BYTES bytes (o @Size conta caracteres)
        }
        val email = normalizar(pedido.email)
        if (usuarios.existsByEmail(email)) throw EmailJaCadastradoException()

        val novo = Usuario(pedido.nome.trim(), email, gerarHash(pedido.senha), PapelUsuario.CIDADAO)
        return try {
            usuarios.saveAndFlush(novo).toResponse()
        } catch (e: DataIntegrityViolationException) {
            throw EmailJaCadastradoException() // duas contas com o mesmo e-mail ao mesmo tempo
        }
    }

    /** Dados da conta do token. Token de conta que não existe mais não vale. */
    @Transactional(readOnly = true)
    fun conta(idDoToken: String): UsuarioResponse {
        val id = idDoToken.toLongOrNull() ?: throw CredenciaisInvalidasException()
        return (usuarios.findById(id).orElse(null) ?: throw CredenciaisInvalidasException()).toResponse()
    }

    private fun gerarHash(senha: String) = requireNotNull(senhas.encode(senha)) { "O codificador de senha devolveu nulo" }

    private fun normalizar(email: String) = email.trim().lowercase(Locale.ROOT)

    private fun String.cabeNoBcrypt() = toByteArray().size <= LIMITE_BCRYPT_BYTES

    private companion object {
        /** O BCrypt só considera os 72 primeiros bytes da senha. */
        const val LIMITE_BCRYPT_BYTES = 72
    }
}
