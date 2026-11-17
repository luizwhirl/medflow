package br.ufal.medflow.auth

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Contas para testar a API em desenvolvimento (perfil `dev`, o mesmo do seed). Nunca roda em produção.
 * Senha de todas: [SENHA].
 */
@Component
@Profile("dev")
class UsuariosDeExemplo(
    private val usuarios: UsuarioRepository,
    private val senhas: PasswordEncoder,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val exemplos = listOf(
            Triple("Dona Rita", "rita@medflow.local", PapelUsuario.CIDADAO),
            Triple("Seu Carlos", "carlos@medflow.local", PapelUsuario.GESTOR),
        )
        val novos = exemplos.filterNot { (_, email, _) -> usuarios.existsByEmail(email) }
            .map { (nome, email, papel) -> Usuario(nome, email, requireNotNull(senhas.encode(SENHA)), papel) }
        if (novos.isNotEmpty()) {
            usuarios.saveAll(novos)
            log.info("Usuários de exemplo criados (só dev, senha \"{}\"): {}", SENHA, novos.joinToString { it.email })
        }
    }

    companion object {
        const val SENHA = "medflow-dev-123"
    }
}
