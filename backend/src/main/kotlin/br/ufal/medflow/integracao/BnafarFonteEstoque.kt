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
        var anterior: Set<RegistroBnafar> = emptySet()

        repeat(props.maxPaginas) { pagina ->
            val registros = buscarPagina(municipio, dataPosicao, pagina)
            // Se a API voltar a tratar `offset` como linhas, as páginas se sobrepõem. Falha em vez de somar duas vezes.
            if (anterior.isNotEmpty() && registros.count { it in anterior } * 2 > registros.size) {
                throw FonteEstoqueIndisponivelException(
                    "BNAFAR repetiu registros entre páginas (município $municipio, página $pagina): o `offset` mudou de significado?",
                )
            }
            anterior = registros.toSet()
            registros.forEach { registro ->
                val posicao = registro.toPosicao()
                if (posicao == null) descartados++ else posicoes += posicao
            }
            if (registros.size < props.tamanhoPagina) {
                if (descartados > 0) log.warn("BNAFAR: {} registros incompletos descartados (município {})", descartados, municipio)
                return posicoes
            }
        }

        throw FonteEstoqueIndisponivelException(
            "BNAFAR: limite de ${props.maxPaginas} páginas atingido para o município $municipio",
        )
    }

    /** Na API, `offset` é o número da página (0, 1, 2...) e não a quantidade de linhas puladas. */
    private fun buscarPagina(municipio: String, dataPosicao: LocalDate?, pagina: Int): List<RegistroBnafar> {
        val resposta = try {
            restClient.get()
                .uri { uri ->
                    uri.path(CAMINHO).queryParam("codigo_municipio", municipio)
                    dataPosicao?.let { uri.queryParam("data_posicao_estoque", it) }
                    uri.queryParam("limit", props.tamanhoPagina).queryParam("offset", pagina).build()
                }
                .retrieve()
                .body(RespostaBnafar::class.java)
        } catch (e: RestClientException) {
            throw FonteEstoqueIndisponivelException("BNAFAR indisponível (município $municipio, página $pagina): ${e.message}", e)
        }
        return (resposta ?: throw FonteEstoqueIndisponivelException("BNAFAR devolveu resposta vazia")).parametros
    }

    private companion object {
        const val CAMINHO = "/daf/estoque-medicamentos-bnafar-horus"
    }
}
