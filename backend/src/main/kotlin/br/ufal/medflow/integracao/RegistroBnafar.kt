package br.ufal.medflow.integracao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class RespostaBnafar(
    val parametros: List<RegistroBnafar> = emptyList(),
)

/** Um registro (lote) como a API da BNAFAR devolve. Só os campos que usamos. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RegistroBnafar(
    @JsonProperty("codigo_municipio") val codigoMunicipio: Int? = null,
    @JsonProperty("codigo_cnes") val codigoCnes: Long? = null,
    @JsonProperty("data_posicao_estoque") val dataPosicaoEstoque: String? = null,
    @JsonProperty("codigo_catmat") val codigoCatmat: String? = null,
    @JsonProperty("descricao_produto") val descricaoProduto: String? = null,
    @JsonProperty("quantidade_estoque") val quantidadeEstoque: BigDecimal? = null,
    @JsonProperty("numero_lote") val numeroLote: String? = null,
    @JsonProperty("data_validade") val dataValidade: String? = null,
    @JsonProperty("nome_fantasia") val nomeFantasia: String? = null,
    @JsonProperty("razao_social") val razaoSocial: String? = null,
    @JsonProperty("logradouro") val logradouro: String? = null,
    @JsonProperty("numero_endereco") val numeroEndereco: String? = null,
    @JsonProperty("bairro") val bairro: String? = null,
    @JsonProperty("cep") val cep: String? = null,
    @JsonProperty("telefone") val telefone: String? = null,
    @JsonProperty("latitude") val latitude: Double? = null,
    @JsonProperty("longitude") val longitude: Double? = null,
)

/** Converte para o formato interno; devolve null se faltar algo essencial (CNES, CATMAT, quantidade, data). */
fun RegistroBnafar.toPosicao(): PosicaoEstoque? {
    val municipio = codigoMunicipio ?: return null
    val cnes = codigoCnes ?: return null
    val catmat = codigoCatmat?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val quantidade = quantidadeEstoque ?: return null
    val dataPosicao = dataPosicaoEstoque.paraData() ?: return null

    return PosicaoEstoque(
        codigoIbgeMunicipio = CodigoIbge.paraSeteDigitos(municipio.toString()),
        cnes = cnes.toString().padStart(7, '0'),
        nomeUnidade = nomeFantasia ?: razaoSocial,
        logradouro = logradouro,
        numero = numeroEndereco,
        bairro = bairro,
        cep = cep,
        telefone = telefone,
        latitude = latitude,
        longitude = longitude,
        catmat = catmat,
        descricaoProduto = descricaoProduto,
        quantidade = quantidade,
        numeroLote = numeroLote,
        validade = dataValidade.paraData(),
        dataPosicao = dataPosicao,
    )
}

/** A API manda data com hora e fuso ("2026-09-30 00:00:00-03"); só o dia interessa. */
private fun String?.paraData(): LocalDate? =
    this?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
