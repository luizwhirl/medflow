package br.ufal.medflow.estoque

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties("app.estoque")
data class EstoqueProperties(
    /** Com saldo até esta quantidade, o item aparece como RESERVADO. */
    val limiteReservado: Int = 20,
) {
    init {
        require(limiteReservado >= 0) { "app.estoque.limite-reservado não pode ser negativo" }
    }
}

/**
 * Deriva o status do estoque a partir da quantidade. A BNAFAR só informa quantidade,
 * então "reservado" é uma regra nossa: saldo baixo, ou saldo que já está todo comprometido com a demanda
 * (igual ou menor que ela). Com 50 em estoque e 50 pessoas esperando, quem chegar depois não leva nada.
 */
@Component
class RegraStatusEstoque(private val props: EstoqueProperties) {

    /**
     * [demanda] é a quantidade de pessoas aguardando o item (lista de desejos), se conhecida.
     * O empate (quantidade == demanda) também é RESERVADO: não sobra nada para quem não está na lista.
     */
    fun calcular(quantidade: Int, demanda: Int? = null): StatusEstoque = when {
        quantidade <= 0 -> StatusEstoque.EM_FALTA
        quantidade <= props.limiteReservado -> StatusEstoque.RESERVADO
        demanda != null && quantidade <= demanda -> StatusEstoque.RESERVADO
        else -> StatusEstoque.DISPONIVEL
    }
}
