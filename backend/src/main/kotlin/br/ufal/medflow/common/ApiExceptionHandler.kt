package br.ufal.medflow.common

import br.ufal.medflow.auth.CredenciaisInvalidasException
import br.ufal.medflow.auth.EmailJaCadastradoException
import br.ufal.medflow.gestao.AcessoNegadoGestaoException
import br.ufal.medflow.integracao.LimiteMunicipioIndisponivelException
import br.ufal.medflow.listadesejo.ItemListaDesejoDuplicadoException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Converte exceções de domínio em respostas RFC 9457 (application/problem+json). */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException::class)
    fun naoEncontrado(ex: RecursoNaoEncontradoException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Recurso não encontrado").apply {
            title = "Recurso não encontrado"
        }

    @ExceptionHandler(ParametroInvalidoException::class)
    fun parametroInvalido(ex: ParametroInvalidoException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Parâmetro inválido").apply {
            title = "Parâmetro inválido"
        }

    @ExceptionHandler(CredenciaisInvalidasException::class)
    fun credenciaisInvalidas(ex: CredenciaisInvalidasException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.message ?: "Credenciais inválidas").apply {
            title = "Credenciais inválidas"
        }

    @ExceptionHandler(AcessoNegadoGestaoException::class)
    fun acessoNegadoGestao(ex: AcessoNegadoGestaoException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message ?: "Acesso negado").apply {
            title = "Acesso negado"
        }

    @ExceptionHandler(EmailJaCadastradoException::class)
    fun emailJaCadastrado(ex: EmailJaCadastradoException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "E-mail já cadastrado").apply {
            title = "E-mail já cadastrado"
        }

    @ExceptionHandler(LimiteMunicipioIndisponivelException::class)
    fun limiteMunicipioIndisponivel(ex: LimiteMunicipioIndisponivelException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Não foi possível conferir a localização com o contorno do município (IBGE). Tente de novo em instantes.",
        ).apply { title = "Serviço do IBGE indisponível" }

    @ExceptionHandler(ItemListaDesejoDuplicadoException::class)
    fun itemListaDesejoDuplicado(ex: ItemListaDesejoDuplicadoException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Item já cadastrado").apply {
            title = "Item já cadastrado"
        }
}
