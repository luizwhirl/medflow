package br.ufal.medflow.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler

/** 401 e 403 do filtro de segurança no mesmo formato (RFC 9457) do resto da API. */
object RespostasDeSeguranca : AuthenticationEntryPoint, AccessDeniedHandler {

    private val padraoBearer = BearerTokenAuthenticationEntryPoint() // preenche o cabeçalho WWW-Authenticate

    override fun commence(request: HttpServletRequest, response: HttpServletResponse, ex: AuthenticationException) {
        padraoBearer.commence(request, response, ex)
        escrever(response, HttpStatus.UNAUTHORIZED, "Não autenticado", "Informe um token válido em Authorization: Bearer <token>")
    }

    override fun handle(request: HttpServletRequest, response: HttpServletResponse, ex: AccessDeniedException) {
        escrever(response, HttpStatus.FORBIDDEN, "Acesso negado", "O seu perfil não tem permissão para esta operação")
    }

    private fun escrever(response: HttpServletResponse, status: HttpStatus, titulo: String, detalhe: String) {
        response.status = status.value()
        response.contentType = "application/problem+json"
        response.characterEncoding = "UTF-8"
        response.writer.write("""{"type":"about:blank","title":"$titulo","status":${status.value()},"detail":"$detalhe"}""")
    }
}
