package br.ufal.medflow.integracao

import br.ufal.medflow.ubs.OrigemLocalizacao
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode
import java.net.http.HttpClient
import java.text.Normalizer
import java.time.Duration
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** Onde a unidade foi achada: no próprio prédio ([OrigemLocalizacao.LUGAR]) ou no centro do bairro ([OrigemLocalizacao.BAIRRO]). */
data class LocalEncontrado(val ponto: Ponto, val origem: OrigemLocalizacao)

/** Confere e acha UBS no mapa. Só devolve pontos dentro do município. */
interface Geocodificador {
    /** `null` quando nada confiável foi achado (ou o serviço falhou): a unidade fica sem localização. */
    fun localizar(nomeUnidade: String, bairro: String?, limite: LimiteMunicipio, toleranciaKm: Double): LocalEncontrado?

    /**
     * Centro do bairro, para conferir se a coordenada da fonte está no lugar certo. `null` quando o bairro não foi
     * achado (nome abreviado, serviço fora): aí não dá para conferir e vale só o contorno do município.
     */
    fun centroDoBairro(bairro: String?, limite: LimiteMunicipio, toleranciaKm: Double): Ponto?
}

@ConfigurationProperties("app.geocodificacao")
data class GeocodificacaoProperties(
    /** Desligado, nenhuma unidade com coordenada errada é procurada: todas ficam sem localização até o gestor corrigir. */
    val ativo: Boolean = true,
    val baseUrl: String = "https://nominatim.openstreetmap.org",
    /** A política do Nominatim pede um User-Agent que identifique a aplicação. */
    val userAgent: String = "medflow-ubs-conecta/1.0 (sincronizacao de estoque)",
    /** A política do Nominatim permite no máximo 1 requisição por segundo. */
    val intervalo: Duration = Duration.ofMillis(1100),
    val timeoutConexao: Duration = Duration.ofSeconds(5),
    val timeoutLeitura: Duration = Duration.ofSeconds(20),
)

/**
 * Procura no OpenStreetMap (Nominatim), restrito ao retângulo do município:
 * 1. a unidade pelo nome, aceitando só lugares de saúde (clínica, posto, prédio com "USF", "UBS"...);
 * 2. sem ela, o bairro (limite administrativo ou lugar), que vira uma localização aproximada.
 * Todo resultado precisa cair dentro do contorno do município no IBGE.
 */
@Component
class NominatimGeocodificador(private val props: GeocodificacaoProperties) : Geocodificador {

    private val log = LoggerFactory.getLogger(javaClass)
    private val trava = Any()
    private var ultimaChamada = 0L

    /** Bairro não muda de lugar: cada um é procurado uma vez por execução da aplicação (achado ou não). */
    private val bairros = ConcurrentHashMap<String, Optional<Ponto>>()

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(props.baseUrl)
        .defaultHeader("User-Agent", props.userAgent)
        .requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(props.timeoutConexao).build())
                .apply { setReadTimeout(props.timeoutLeitura) },
        )
        .build()

    override fun localizar(nomeUnidade: String, bairro: String?, limite: LimiteMunicipio, toleranciaKm: Double): LocalEncontrado? {
        if (!props.ativo) return null
        return try {
            NomesParaBusca.unidade(nomeUnidade)
                ?.let { nome -> buscar(nome, limite).firstOrNull { ehLugarDeSaude(it) && dentro(it, limite, toleranciaKm) } }
                ?.let { LocalEncontrado(ponto(it), OrigemLocalizacao.LUGAR) }
                ?: centroDoBairro(bairro, limite, toleranciaKm)?.let { LocalEncontrado(it, OrigemLocalizacao.BAIRRO) }
        } catch (e: RestClientException) {
            log.warn("Nominatim indisponível ao procurar {} ({}): {}", nomeUnidade, bairro, e.message)
            null
        }
    }

    override fun centroDoBairro(bairro: String?, limite: LimiteMunicipio, toleranciaKm: Double): Ponto? {
        if (!props.ativo) return null
        val nome = NomesParaBusca.bairro(bairro) ?: return null
        val chave = "$nome|${limite.caixa()}"
        bairros[chave]?.let { return it.orElse(null) }
        return try {
            val achado = buscar(nome, limite).firstOrNull { ehBairro(it) && dentro(it, limite, toleranciaKm) }?.let(::ponto)
            bairros[chave] = Optional.ofNullable(achado)
            achado
        } catch (e: RestClientException) {
            log.warn("Nominatim indisponível ao procurar o bairro {}: {}", bairro, e.message)
            null // não guarda: tenta de novo na próxima sincronização
        }
    }

    private fun buscar(texto: String, limite: LimiteMunicipio): List<JsonNode> {
        esperarVez()
        val caixa = limite.caixa()
        val resposta = restClient.get()
            .uri {
                it.path("/search")
                    .queryParam("q", texto)
                    .queryParam("format", "jsonv2")
                    .queryParam("countrycodes", "br")
                    .queryParam("limit", 5)
                    .queryParam("bounded", 1)
                    .queryParam("viewbox", "${caixa.lngMin},${caixa.latMax},${caixa.lngMax},${caixa.latMin}")
                    .build()
            }
            .retrieve()
            .body(JsonNode::class.java)
        return resposta?.let { r -> (0 until r.size()).map { r.get(it) } }.orEmpty()
    }

    private fun esperarVez() = synchronized(trava) {
        val espera = ultimaChamada + props.intervalo.toMillis() - System.currentTimeMillis()
        if (espera > 0) Thread.sleep(espera)
        ultimaChamada = System.currentTimeMillis()
    }

    private fun ponto(r: JsonNode) = Ponto(r.path("lat").asString().toDouble(), r.path("lon").asString().toDouble())

    private fun dentro(r: JsonNode, limite: LimiteMunicipio, toleranciaKm: Double) =
        runCatching { limite.contem(ponto(r), toleranciaKm) }.getOrDefault(false)

    private fun ehLugarDeSaude(r: JsonNode): Boolean {
        val categoria = r.path("category").asString()
        val tipo = r.path("type").asString()
        return when (categoria) {
            "healthcare" -> true
            "amenity" -> tipo in setOf("clinic", "doctors", "hospital", "health_post", "dentist")
            "building" -> NomesParaBusca.normalizar(r.path("display_name").asString()).contains(PALAVRA_DE_SAUDE)
            else -> false
        }
    }

    private fun ehBairro(r: JsonNode): Boolean {
        val categoria = r.path("category").asString()
        val tipo = r.path("type").asString()
        return (categoria == "boundary" && tipo == "administrative") ||
            (categoria == "place" && tipo in setOf("suburb", "neighbourhood", "quarter", "village", "hamlet"))
    }

    private companion object {
        val PALAVRA_DE_SAUDE = Regex("\\b(USF|UBS|PSF|ESF|SAUDE|POSTO)\\b")
    }
}

/** Limpa os nomes da BNAFAR (caixa alta, abreviados, com o distrito sanitário) para a busca no mapa. */
object NomesParaBusca {

    private val DISTRITO = Regex("\\b\\d+\\s*DS\\b")
    private val PREFIXO = Regex(
        "^(UNIDADE DE SAUDE DA FAMILIA|UNID DE SAUDE DA FAMILIA|UNIDADE DE SAUDE DO PROGRAMA DA FAMILIA|" +
            "UNIDADE BASICA DE SAUDE|UNID BASICA DE SAUDE|UNIDADE DE SAUDE|CENTRO DE SAUDE|US DA FAMILIA|" +
            "POSTO DE SAUDE|C S|US|USF|PSF|UBS|URS|UDA)\\b",
    )
    private val ABREVIACOES = listOf(
        Regex("\\bB BENTES\\b") to "BENEDITO BENTES",
        Regex("\\bCID\\b") to "CIDADE",
        Regex("\\bCONJ\\b") to "CONJUNTO",
        Regex("\\bJD\\b") to "JARDIM",
        Regex("\\bSTA\\b") to "SANTA",
        Regex("\\bSTO\\b") to "SANTO",
    )

    fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").uppercase()
            .replace(Regex("[^A-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Nome sem o tipo de unidade e o distrito: "UNIDADE DE SAUDE DA FAMILIA DURVAL CORTEZ 2 DS" vira "DURVAL CORTEZ". */
    fun unidade(nome: String): String? =
        normalizar(nome).replace(DISTRITO, "").replace(PREFIXO, "").replace(Regex("\\s+"), " ").trim()
            .takeIf { it.length >= 4 }

    /**
     * Outro bairro da lista com nome quase igual (1 letra de diferença; 2 em nomes de 10 letras ou mais), para nomes
     * digitados errado na fonte: "OURO PREO" acha "OURO PRETO". Entre os empatados, o que mais aparece. Nomes curtos
     * não contam, e "CENTRAL" não vira "CENTRO".
     */
    fun parecido(bairro: String?, candidatos: Collection<String>): String? {
        val alvo = bairro?.let(::normalizar)?.takeIf { it.length >= 6 } ?: return null
        return candidatos
            .groupingBy(::normalizar).eachCount()
            .filterKeys { it != alvo && it.length >= 6 }
            .map { (nome, vezes) -> Triple(nome, distancia(alvo, nome), vezes) }
            .filter { it.second <= if (alvo.length >= 10) 2 else 1 }
            .minWithOrNull(compareBy<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third })
            ?.first
    }

    /** Distância de edição (Levenshtein). */
    private fun distancia(a: String, b: String): Int {
        var anterior = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val atual = IntArray(b.length + 1)
            atual[0] = i
            for (j in 1..b.length) {
                atual[j] = minOf(anterior[j] + 1, atual[j - 1] + 1, anterior[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            anterior = atual
        }
        return anterior[b.length]
    }

    fun bairro(bairro: String?): String? {
        var b = normalizar(bairro ?: return null)
        ABREVIACOES.forEach { (de, para) -> b = b.replace(de, para) }
        return b.takeIf { it.length >= 3 && it != "NAO INFORMADO" }
    }
}
