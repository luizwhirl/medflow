package br.ufal.medflow.integracao

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Ponto em graus decimais. */
data class Ponto(val latitude: Double, val longitude: Double) {
    /** Distância em km (Haversine). */
    fun distanciaKm(outro: Ponto): Double {
        val dLat = Math.toRadians(outro.latitude - latitude)
        val dLng = Math.toRadians(outro.longitude - longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(outro.latitude)) * sin(dLng / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(min(1.0, a)))
    }
}

/**
 * Contorno de um município (malha do IBGE): um ou mais polígonos, cada um com o anel externo e, se houver, buracos.
 * Cada anel é uma lista de pontos fechada ou não (o último ponto pode repetir o primeiro).
 */
class LimiteMunicipio(private val poligonos: List<List<List<Ponto>>>) {

    init {
        require(poligonos.isNotEmpty() && poligonos.all { it.isNotEmpty() && it.first().size >= 3 }) {
            "O contorno do município precisa de ao menos um polígono com 3 pontos"
        }
    }

    /**
     * Se o ponto está dentro do município ou a até [toleranciaKm] da borda.
     *
     * A malha do IBGE é simplificada (algumas centenas de pontos por município): no litoral a linha passa por dentro
     * da praia e unidades certas ficam centenas de metros "fora". A tolerância absorve isso sem aceitar erros grandes.
     */
    fun contem(ponto: Ponto, toleranciaKm: Double): Boolean =
        estaDentro(ponto) || (toleranciaKm > 0 && distanciaDaBordaKm(ponto) <= toleranciaKm)

    data class Caixa(val latMin: Double, val latMax: Double, val lngMin: Double, val lngMax: Double)

    /** Retângulo que envolve o município. */
    fun caixa(): Caixa {
        val pontos = poligonos.flatMap { it.first() }
        return Caixa(pontos.minOf { it.latitude }, pontos.maxOf { it.latitude }, pontos.minOf { it.longitude }, pontos.maxOf { it.longitude })
    }

    /** Par-ímpar: dentro do anel externo e fora dos buracos de algum polígono. */
    fun estaDentro(ponto: Ponto): Boolean = poligonos.any { aneis ->
        dentroDoAnel(ponto, aneis.first()) && aneis.drop(1).none { dentroDoAnel(ponto, it) }
    }

    /** Distância (km) do ponto até o segmento de borda mais próximo, numa projeção local (boa para poucos km). */
    fun distanciaDaBordaKm(ponto: Ponto): Double {
        val kmPorGrauLng = KM_POR_GRAU_LAT * cos(Math.toRadians(ponto.latitude))
        var menor = Double.MAX_VALUE
        for (anel in poligonos.flatten()) {
            for (i in anel.indices) {
                val a = anel[i]
                val b = anel[(i + 1) % anel.size]
                val ax = (a.longitude - ponto.longitude) * kmPorGrauLng
                val ay = (a.latitude - ponto.latitude) * KM_POR_GRAU_LAT
                val dx = (b.longitude - a.longitude) * kmPorGrauLng
                val dy = (b.latitude - a.latitude) * KM_POR_GRAU_LAT
                val comprimento2 = dx * dx + dy * dy
                val t = if (comprimento2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / comprimento2).coerceIn(0.0, 1.0)
                menor = minOf(menor, hypot(ax + t * dx, ay + t * dy))
            }
        }
        return menor
    }

    private fun dentroDoAnel(p: Ponto, anel: List<Ponto>): Boolean {
        var dentro = false
        var j = anel.lastIndex
        for (i in anel.indices) {
            val (yi, xi) = anel[i]
            val (yj, xj) = anel[j]
            if ((yi > p.latitude) != (yj > p.latitude) &&
                p.longitude < (xj - xi) * (p.latitude - yi) / (yj - yi) + xi
            ) {
                dentro = !dentro
            }
            j = i
        }
        return dentro
    }

    private companion object {
        const val KM_POR_GRAU_LAT = 110.574
    }
}

/** Contorno oficial dos municípios, para conferir as coordenadas que vêm da fonte. */
fun interface LimitesMunicipais {
    /**
     * @param codigoIbge IBGE com 7 dígitos
     * @throws LimiteMunicipioIndisponivelException se não for possível obter o contorno
     */
    fun limite(codigoIbge: String): LimiteMunicipio
}

class LimiteMunicipioIndisponivelException(mensagem: String, causa: Throwable? = null) : RuntimeException(mensagem, causa)
