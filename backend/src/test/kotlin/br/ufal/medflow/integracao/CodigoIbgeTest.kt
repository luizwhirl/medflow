package br.ufal.medflow.integracao

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CodigoIbgeTest {

    @Test
    fun `completa o digito verificador de municipios conhecidos`() {
        assertEquals("2704302", CodigoIbge.paraSeteDigitos("270430")) // Maceió
        assertEquals("2700300", CodigoIbge.paraSeteDigitos("270030")) // Arapiraca
        assertEquals("2702405", CodigoIbge.paraSeteDigitos("270240")) // Delmiro Gouveia
    }

    @Test
    fun `municipios que fogem da regra do digito verificador usam o codigo oficial`() {
        val oficiais = mapOf(
            "220191" to "2201919", // Bom Princípio do Piauí
            "220198" to "2201988", // Brejo do Piauí
            "220225" to "2202251", // Canavieira
            "261153" to "2611533", // Quixaba
            "311783" to "3117836", // Cônego Marinho
            "315213" to "3152131", // Ponto Chique
            "430587" to "4305871", // Coronel Barros
            "520393" to "5203939", // Buriti de Goiás
            "520396" to "5203962", // Buritinópolis
        )

        oficiais.forEach { (seis, sete) ->
            assertEquals(sete, CodigoIbge.paraSeteDigitos(seis), "município $seis")
            assertEquals(seis, CodigoIbge.paraSeisDigitos(sete), "município $sete")
        }
    }

    @Test
    fun `mantem o codigo que ja tem 7 digitos`() {
        assertEquals("2704302", CodigoIbge.paraSeteDigitos("2704302"))
    }

    @Test
    fun `reduz para 6 digitos`() {
        assertEquals("270430", CodigoIbge.paraSeisDigitos("2704302"))
        assertEquals("270430", CodigoIbge.paraSeisDigitos("270430"))
    }

    @Test
    fun `rejeita codigo invalido`() {
        assertFailsWith<IllegalArgumentException> { CodigoIbge.paraSeteDigitos("27043") }
        assertFailsWith<IllegalArgumentException> { CodigoIbge.paraSeteDigitos("27043021") }
        assertFailsWith<IllegalArgumentException> { CodigoIbge.paraSeisDigitos("27a430") }
    }
}
