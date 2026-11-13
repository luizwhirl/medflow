package br.ufal.medflow.common

/** Indica que um parâmetro informado na requisição é inválido. */
class ParametroInvalidoException(parametro: String) :
    RuntimeException("Parâmetro '$parametro' inválido")
