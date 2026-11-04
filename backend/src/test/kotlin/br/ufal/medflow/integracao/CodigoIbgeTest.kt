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
