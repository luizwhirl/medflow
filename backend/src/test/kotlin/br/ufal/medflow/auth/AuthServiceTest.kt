package br.ufal.medflow.auth

import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** Só a decisão do que a violação de integridade significa. O resto do cadastro está em AutenticacaoIntegrationTests. */
class AuthServiceTest {

    private val usuarios = mock(UsuarioRepository::class.java)
    private val senhas = mock(PasswordEncoder::class.java)
    private val servico = AuthService(usuarios, senhas, mock(JwtService::class.java))
    private val pedido = CadastroRequest("Rita", "rita@teste.com", "senha-valida-123")

    private fun salvarFalhaCom(erro: Throwable) {
        given(senhas.encode(pedido.senha)).willReturn("hash")
        given(usuarios.existsByEmail("rita@teste.com")).willReturn(false)
        given(usuarios.saveAndFlush(any(Usuario::class.java) ?: Usuario("", "", "", PapelUsuario.CIDADAO))).willThrow(erro)
    }

    private fun violacao(estadoSql: String) = DataIntegrityViolationException(
        "violação",
        ConstraintViolationException("violação", SQLException("detalhe", estadoSql), "usuario_email_key"),
    )

    @Test
    fun `violacao de unicidade vira e-mail ja cadastrado`() {
        salvarFalhaCom(violacao("23505"))

        assertFailsWith<EmailJaCadastradoException> { servico.cadastrar(pedido) }
    }

    @Test
    fun `outra violacao de integridade nao e disfarcada de e-mail repetido`() {
        val checkViolado = violacao("23514") // CHECK (email = LOWER(email)) ou outra restrição
        salvarFalhaCom(checkViolado)

        val erro = assertFailsWith<DataIntegrityViolationException> { servico.cadastrar(pedido) }

        assertSame(checkViolado, erro)
    }

    @Test
    fun `violacao sem SQLException na causa tambem nao vira e-mail repetido`() {
        salvarFalhaCom(DataIntegrityViolationException("sem causa de SQL"))

        assertEquals("sem causa de SQL", assertFailsWith<DataIntegrityViolationException> { servico.cadastrar(pedido) }.message)
    }
}
