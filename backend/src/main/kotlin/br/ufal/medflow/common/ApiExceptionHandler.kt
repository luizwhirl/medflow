package br.ufal.medflow.common

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
}
