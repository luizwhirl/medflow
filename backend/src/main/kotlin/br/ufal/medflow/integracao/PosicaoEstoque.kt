package br.ufal.medflow.integracao

import java.math.BigDecimal
import java.time.LocalDate

/** Posição de estoque de um lote em uma unidade, já no formato interno (IBGE com 7 dígitos). */
data class PosicaoEstoque(
    val codigoIbgeMunicipio: String,
    val cnes: String,
    val nomeUnidade: String?,
    val logradouro: String?,
    val numero: String?,
    val bairro: String?,
    val cep: String?,
    val telefone: String?,
    val latitude: Double?,
    val longitude: Double?,
    /** Código como vem da fonte; pode trazer sufixo de unidade (ex.: BR0267691U0042). */
    val catmat: String,
    val descricaoProduto: String?,
    val quantidade: BigDecimal,
    val numeroLote: String?,
    val validade: LocalDate?,
    val dataPosicao: LocalDate,
)
