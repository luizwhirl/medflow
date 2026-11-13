package br.ufal.medflow.common

/** Indica que um parâmetro informado na requisição é inválido. A [mensagem] vai no `detail` da resposta 400. */
class ParametroInvalidoException(mensagem: String) : RuntimeException(mensagem) {
    companion object {
        /** Mensagem padrão para um parâmetro com valor inválido: "Parâmetro 'x' inválido". */
        fun de(parametro: String) = ParametroInvalidoException("Parâmetro '$parametro' inválido")
    }
}
