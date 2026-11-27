package br.ufal.medflow.sincronizacao

import br.ufal.medflow.ubs.TipoUnidade
import java.text.Normalizer

/**
 * A BNAFAR devolve qualquer unidade que movimenta estoque: secretaria, almoxarifado (CAF), CAPS, UPA...
 * O app é para o cidadão retirar o remédio, então só entram UBS, USF e farmácias da rede.
 */
object ClassificacaoUnidade {

    private val NAO_ATENDE_CIDADAO = Regex(
        "\\b(SECRETARIA|ALMOXARIFADO|CAF|CAPS|UPA|HOSPITAL|MATERNIDADE|SAMU|LABORATORIO|VIGILANCIA)\\b|" +
            "CENTRAL DE ABASTECIMENTO|CENTRO ESPECIALIZADO|APOIO PSICOSSOCIAL",
    )
    private val SAUDE_DA_FAMILIA = Regex("\\b(USF|ESF|PSF)\\b|SAUDE DA FAMILIA|\\bUS DA FAMILIA\\b|CLINICA DA FAMILIA")

    /** A BNAFAR abrevia à vontade: "UNID BASICA", "US FREI DAMIAO", "C S ROLAND SIMON", "UNIDADE DE S JOSE...", "UDA". */
    private val UNIDADE_BASICA = Regex(
        "\\bUBS\\b|UNID(ADE)? BASICA|UNID(ADE)? DE SAUDE|UNIDADE DE S\\b|CENTRO DE SAUDE|\\bC S\\b|\\bUS\\b|" +
            "\\bUDA\\b|UNIDADE DOCENTE ASSISTENCIAL|\\bPOSTO\\b|FARMACIA",
    )

    /** Devolve o tipo da unidade, ou null se ela não é um ponto de atendimento ao cidadão. */
    fun tipo(nome: String?): TipoUnidade? {
        val n = Normalizer.normalize(nome ?: return null, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").uppercase()
        return when {
            NAO_ATENDE_CIDADAO.containsMatchIn(n) -> null
            SAUDE_DA_FAMILIA.containsMatchIn(n) -> TipoUnidade.USF
            UNIDADE_BASICA.containsMatchIn(n) -> TipoUnidade.UBS
            else -> null
        }
    }
}
