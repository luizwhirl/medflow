package br.ufal.medflow.ubs

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Retângulo de latitude/longitude que contém, com folga, todos os pontos a até `raioKm` do centro.
 * Serve para o banco descartar as unidades distantes antes da conta de distância; quem decide quem está
 * dentro do raio continua sendo a distância. Nunca corta ponto que está dentro do raio.
 */
data class CaixaGeografica(val latMin: Double, val latMax: Double, val lngMin: Double, val lngMax: Double) {

    companion object {
        private const val FOLGA_GRAUS = 1e-6

        fun em(lat: Double, lng: Double, raioKm: Double): CaixaGeografica {
            val angulo = raioKm / UbsRepository.RAIO_DA_TERRA_KM // distância angular, em radianos
            if (angulo >= Math.PI / 2) return CaixaGeografica(-90.0, 90.0, -180.0, 180.0)

            val deltaLat = Math.toDegrees(angulo)
            val latMin = lat - deltaLat - FOLGA_GRAUS
            val latMax = lat + deltaLat + FOLGA_GRAUS
            // se o raio alcança um polo, todas as longitudes entram
            if (latMin <= -90.0 || latMax >= 90.0) return CaixaGeografica(latMin.coerceAtLeast(-90.0), latMax.coerceAtMost(90.0), -180.0, 180.0)

            val deltaLng = Math.toDegrees(asin(sin(angulo) / cos(Math.toRadians(lat)))) + FOLGA_GRAUS
            val lngMin = lng - deltaLng
            val lngMax = lng + deltaLng
            // se cruza o meridiano 180, não dá para limitar por uma faixa só
            if (lngMin < -180.0 || lngMax > 180.0) return CaixaGeografica(latMin, latMax, -180.0, 180.0)

            return CaixaGeografica(latMin, latMax, lngMin, lngMax)
        }
    }
}
