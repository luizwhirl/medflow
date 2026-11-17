package br.ufal.medflow.common

/**
 * Indica que um parâmetro informado na requisição é inválido. Sem [detalhe], a mensagem é
 * "Parâmetro 'x' inválido"; com ele, o detalhe explica o motivo (ex.: parâmetros que só valem juntos).
 */
class ParametroInvalidoException(val parametro: String, detalhe: String? = null) :
    RuntimeException(detalhe ?: "Parâmetro '$parametro' inválido")
