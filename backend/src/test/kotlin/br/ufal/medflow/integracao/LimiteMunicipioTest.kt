package br.ufal.medflow.integracao

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contorno de Maceió como o IBGE devolve (malha em qualidade máxima, baixada em 25/11/2026) e coordenadas reais que
 * a BNAFAR mandou para unidades de Maceió na mesma data.
 */
class LimiteMunicipioTest {

    private val maceio = limiteDoGeoJson(
        JsonMapper.builder().build().readTree(requireNotNull(javaClass.getResource("/ibge/malha-maceio.json")).readText()),
    )
    private val tolerancia = 3.5

    @Test
    fun `aceita unidade no meio da cidade`() {
        // UBS Denisson Menezes, Tabuleiro
        assertTrue(maceio.estaDentro(Ponto(-9.5441295, -35.72848320007324)))
        assertTrue(maceio.contem(Ponto(-9.5441295, -35.72848320007324), tolerancia))
    }

    @Test
    fun `recusa a coordenada no mar que a BNAFAR manda para a US Aliomar Almeida Lins`() {
        val ponto = Ponto(-9.5598645, -34.8591955)
        assertFalse(maceio.contem(ponto, tolerancia))
        assertTrue(maceio.distanciaDaBordaKm(ponto) > 70)
    }

    @Test
    fun `recusa coordenadas de outras cidades mandadas para unidades de Maceio`() {
        listOf(
            Ponto(-9.6550788, -36.1819301), // PSF Vila Brejal (Levada)
            Ponto(-9.6005719, -36.0617557), // UBS Maria Tereza Holanda Costa (Ouro Preto)
            Ponto(-9.584642, -35.9215416), // US Grota do Moreira (Jacintinho)
            Ponto(-9.4761407, -35.8558626), // USF Ouro Preto
            Ponto(-9.5953471, -35.8493383), // URS Ib Gatto Falcão (Tabuleiro)
        ).forEach { assertFalse(maceio.contem(it, tolerancia), "$it deveria ficar fora de Maceió") }
    }

    @Test
    fun `aceita unidades do litoral que a malha simplificada deixa um pouco para fora`() {
        listOf(
            Ponto(-9.5768499, -35.6083538), // CS David Nasser, Ipioca: 3,3 km fora da malha
            Ponto(-9.6184323, -35.67089080810547), // UBS Vanderli Maria de Andrade, Riacho Doce: 1,6 km
            Ponto(-9.6894204, -35.759124755859375), // CS Tarcísio Palmeira, Pontal da Barra: 0,5 km
        ).forEach {
            assertFalse(maceio.estaDentro(it))
            assertTrue(maceio.contem(it, tolerancia), "$it deveria valer com a tolerância")
            assertFalse(maceio.contem(it, toleranciaKm = 0.0))
        }
    }

    @Test
    fun `distancia entre pontos em km`() {
        // US Geraldo Melo: coordenada da BNAFAR (litoral norte) até o centro do Bom Parto, bairro dela
        val distancia = Ponto(-9.4656804, -35.5535229).distanciaKm(Ponto(-9.6466, -35.7543))
        assertTrue(distancia in 28.0..32.0, "deu $distancia")
        assertEquals(0.0, Ponto(-9.6, -35.7).distanciaKm(Ponto(-9.6, -35.7)))
    }

    @Test
    fun `caixa envolve o municipio`() {
        val caixa = maceio.caixa()
        assertTrue(caixa.latMin < -9.7 && caixa.latMax > -9.4)
        assertTrue(caixa.lngMin < -35.8 && caixa.lngMax > -35.6)
    }

    @Test
    fun `buraco do poligono fica de fora`() {
        val externo = listOf(Ponto(0.0, 0.0), Ponto(0.0, 10.0), Ponto(10.0, 10.0), Ponto(10.0, 0.0))
        val buraco = listOf(Ponto(4.0, 4.0), Ponto(4.0, 6.0), Ponto(6.0, 6.0), Ponto(6.0, 4.0))
        val limite = LimiteMunicipio(listOf(listOf(externo, buraco)))
        assertTrue(limite.estaDentro(Ponto(1.0, 1.0)))
        assertFalse(limite.estaDentro(Ponto(5.0, 5.0)))
    }

    @Test
    fun `GeoJSON sem poligono e recusado`() {
        val semPoligono = JsonMapper.builder().build().readTree("""{"type":"Point","coordinates":[-35.7,-9.6]}""")
        assertFailsWith<IllegalArgumentException> { limiteDoGeoJson(semPoligono) }
    }

    @Test
    fun `le MultiPolygon`() {
        val json = """{"type":"MultiPolygon","coordinates":[
            [[[0,0],[0,1],[1,1],[1,0],[0,0]]],
            [[[5,5],[5,6],[6,6],[6,5],[5,5]]]
        ]}"""
        val limite = limiteDoGeoJson(JsonMapper.builder().build().readTree(json))
        assertTrue(limite.estaDentro(Ponto(0.5, 0.5)))
        assertTrue(limite.estaDentro(Ponto(5.5, 5.5)))
        assertFalse(limite.estaDentro(Ponto(3.0, 3.0)))
        assertEquals(0.0, limite.caixa().latMin)
    }
}
