package br.ufal.medflow.integracao

import tools.jackson.databind.JsonNode
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@ConfigurationProperties("app.ibge")
data class IbgeProperties(
    val baseUrl: String = "https://servicodados.ibge.gov.br",
    val timeoutConexao: Duration = Duration.ofSeconds(5),
    val timeoutLeitura: Duration = Duration.ofSeconds(30),
)

/**
 * Contorno dos municípios pela API de malhas do IBGE (GeoJSON, sem autenticação).
 * O contorno não muda no dia a dia, então fica em memória: só a primeira sincronização de cada município o busca.
 */
@Component
class IbgeLimitesMunicipais(props: IbgeProperties) : LimitesMunicipais {

    private val cache = ConcurrentHashMap<String, LimiteMunicipio>()

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(props.baseUrl)
        .requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(props.timeoutConexao).build())
                .apply { setReadTimeout(props.timeoutLeitura) },
        )
        .build()

    override fun limite(codigoIbge: String): LimiteMunicipio {
        val ibge = CodigoIbge.paraSeteDigitos(codigoIbge)
        return cache[ibge] ?: buscar(ibge).also { cache[ibge] = it }
    }

    private fun buscar(ibge: String): LimiteMunicipio {
        val geojson = try {
            restClient.get()
                .uri { it.path("/api/v3/malhas/municipios/{ibge}").queryParam("formato", "application/vnd.geo+json").queryParam("qualidade", "maxima").build(ibge) }
                .retrieve()
                .body(JsonNode::class.java)
        } catch (e: RestClientException) {
            throw LimiteMunicipioIndisponivelException("IBGE indisponível ao buscar o contorno do município $ibge: ${e.message}", e)
        }
        return try {
            limiteDoGeoJson(geojson ?: throw IllegalArgumentException("resposta vazia"))
        } catch (e: IllegalArgumentException) {
            throw LimiteMunicipioIndisponivelException("Contorno do município $ibge inválido: ${e.message}", e)
        }
    }
}

/** Lê Polygon ou MultiPolygon de um GeoJSON (FeatureCollection, Feature ou a geometria direto). */
fun limiteDoGeoJson(no: JsonNode): LimiteMunicipio {
    val geometrias = when (no.path("type").asString()) {
        "FeatureCollection" -> no.path("features").filhos().map { it.path("geometry") }
        "Feature" -> listOf(no.path("geometry"))
        else -> listOf(no)
    }
    val poligonos: List<List<List<Ponto>>> = geometrias.flatMap { g ->
        when (g.path("type").asString()) {
            "Polygon" -> listOf(poligono(g.path("coordinates")))
            "MultiPolygon" -> g.path("coordinates").filhos().map { poligono(it) }
            else -> emptyList()
        }
    }
    require(poligonos.isNotEmpty()) { "sem Polygon ou MultiPolygon" }
    return LimiteMunicipio(poligonos)
}

/** GeoJSON usa [longitude, latitude]. */
private fun poligono(aneis: JsonNode): List<List<Ponto>> =
    aneis.filhos().map { anel ->
        anel.filhos().map { par -> Ponto(latitude = par.get(1).asDouble(), longitude = par.get(0).asDouble()) }
    }

private fun JsonNode.filhos(): List<JsonNode> = (0 until size()).map { get(it) }
