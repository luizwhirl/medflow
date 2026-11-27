package br.ufal.medflow.ubs

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CaixaGeograficaTest {

    private val umGrauKm = 6371.0 * Math.PI / 180 // 111,19 km

    @Test
    fun `no equador a caixa tem a mesma abertura em latitude e longitude`() {
        val caixa = CaixaGeografica.em(0.0, 0.0, umGrauKm)

        assertEquals(-1.0, caixa.latMin, 0.001)
        assertEquals(1.0, caixa.latMax, 0.001)
        assertEquals(-1.0, caixa.lngMin, 0.001)
        assertEquals(1.0, caixa.lngMax, 0.001)
    }

    @Test
    fun `em latitude alta a faixa de longitude e mais larga`() {
        // a 60 graus de latitude, 1 grau de longitude vale a metade: a faixa dobra
        val caixa = CaixaGeografica.em(60.0, 10.0, umGrauKm)

        assertEquals(59.0, caixa.latMin, 0.001)
        assertEquals(61.0, caixa.latMax, 0.001)
        assertEquals(8.0, caixa.lngMin, 0.01)
        assertEquals(12.0, caixa.lngMax, 0.01)
    }

    @Test
    fun `quando o raio alcanca um polo todas as longitudes entram`() {
        val caixa = CaixaGeografica.em(89.9, 10.0, 50.0)

        assertEquals(-180.0, caixa.lngMin)
        assertEquals(180.0, caixa.lngMax)
        assertEquals(90.0, caixa.latMax)
    }

    @Test
    fun `quando cruza o meridiano 180 todas as longitudes entram`() {
        val caixa = CaixaGeografica.em(0.0, 179.9, 50.0)

        assertEquals(-180.0, caixa.lngMin)
        assertEquals(180.0, caixa.lngMax)
    }

    @Test
    fun `raio enorme cobre o planeta`() {
        assertEquals(CaixaGeografica(-90.0, 90.0, -180.0, 180.0), CaixaGeografica.em(-9.0, -35.0, 20_100.0))
    }

    @Test
    fun `a caixa sempre contem o centro`() {
        val caixa = CaixaGeografica.em(-9.5, -35.7, 20.0)

        assertEquals(true, -9.5 in caixa.latMin..caixa.latMax)
        assertEquals(true, -35.7 in caixa.lngMin..caixa.lngMax)
    }
}
