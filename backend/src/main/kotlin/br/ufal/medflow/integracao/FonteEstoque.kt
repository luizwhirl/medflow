package br.ufal.medflow.integracao

import java.time.LocalDate

/** Fonte externa de posições de estoque (hoje, a API aberta da BNAFAR). */
interface FonteEstoque {

    /**
     * Posições de estoque de um município, uma por lote. Aceita o IBGE com 6 ou 7 dígitos.
     * Sem [dataPosicao], a fonte devolve a posição que tiver.
     *
     * @throws FonteEstoqueIndisponivelException se a fonte falhar ou não responder
     */
    fun buscarPosicoes(codigoIbge: String, dataPosicao: LocalDate? = null): List<PosicaoEstoque>
}

/** A fonte não respondeu ou devolveu erro. Quem chama decide se tenta de novo. */
class FonteEstoqueIndisponivelException(mensagem: String, causa: Throwable? = null) :
    RuntimeException(mensagem, causa)
