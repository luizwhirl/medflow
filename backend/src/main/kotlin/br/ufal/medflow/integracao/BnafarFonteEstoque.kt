package br.ufal.medflow.integracao

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.LocalDate

/** Lê o estoque da API aberta da BNAFAR (somente leitura, sem autenticação). */
@Component
class BnafarFonteEstoque(
    private val restClient: RestClient,
    private val props: BnafarProperties,
) : FonteEstoque {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun buscarPosicoes(codigoIbge: String, dataPosicao: LocalDate?): List<PosicaoEstoque> {
        val municipio = CodigoIbge.paraSeisDigitos(codigoIbge)
        val posicoes = mutableListOf<PosicaoEstoque>()
        var descartados = 0
        var offset = 0

        repeat(props.maxPaginas) {
            val registros = buscarPagina(municipio, dataPosicao, offset)
            registros.forEach { registro ->
                val posicao = registro.toPosicao()
                if (posicao == null) descartados++ else posicoes += posicao
            }
            if (registros.size < props.tamanhoPagina) {
                if (descartados > 0) log.warn("BNAFAR: {} registros incompletos descartados (município {})", descartados, municipio)
                return posicoes
            }
            offset += props.tamanhoPagina
        }

        throw FonteEstoqueIndisponivelException(
            "BNAFAR: limite de ${props.maxPaginas} páginas atingido para o município $municipio",
        )
    }

    private fun buscarPagina(municipio: String, dataPosicao: LocalDate?, offset: Int): List<RegistroBnafar> {
        val resposta = try {
            restClient.get()
                .uri { uri ->
                    uri.path(CAMINHO).queryParam("codigo_municipio", municipio)
                    dataPosicao?.let { uri.queryParam("data_posicao_estoque", it) }
                    uri.queryParam("limit", props.tamanhoPagina).queryParam("offset", offset).build()
                }
                .retrieve()
                .body(RespostaBnafar::class.java)
        } catch (e: RestClientException) {
            throw FonteEstoqueIndisponivelException("BNAFAR indisponível (município $municipio, offset $offset): ${e.message}", e)
        }
        return (resposta ?: throw FonteEstoqueIndisponivelException("BNAFAR devolveu resposta vazia")).parametros
    }

    private companion object {
        const val CAMINHO = "/daf/estoque-medicamentos-bnafar-horus"
    }
}
