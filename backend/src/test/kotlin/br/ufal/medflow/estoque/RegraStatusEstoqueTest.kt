package br.ufal.medflow.estoque

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class RegraStatusEstoqueTest {

    private val regra = RegraStatusEstoque(EstoqueProperties(limiteReservado = 20))

    @Test
    fun `quantidade zero ou negativa e em falta`() {
        assertEquals(StatusEstoque.EM_FALTA, regra.calcular(0))
        assertEquals(StatusEstoque.EM_FALTA, regra.calcular(-3))
    }

    @Test
    fun `saldo ate o limite e reservado`() {
        assertEquals(StatusEstoque.RESERVADO, regra.calcular(1))
        assertEquals(StatusEstoque.RESERVADO, regra.calcular(19))
        assertEquals(StatusEstoque.RESERVADO, regra.calcular(20))
    }

    @Test
    fun `saldo acima do limite e disponivel`() {
        assertEquals(StatusEstoque.DISPONIVEL, regra.calcular(21))
        assertEquals(StatusEstoque.DISPONIVEL, regra.calcular(5000))
    }

    @Test
    fun `saldo menor ou igual a demanda e reservado`() {
        assertEquals(StatusEstoque.RESERVADO, regra.calcular(50, demanda = 51))
        assertEquals(StatusEstoque.RESERVADO, regra.calcular(50, demanda = 50))
    }

    @Test
    fun `saldo maior que a demanda continua disponivel`() {
        assertEquals(StatusEstoque.DISPONIVEL, regra.calcular(50, demanda = 49))
        assertEquals(StatusEstoque.DISPONIVEL, regra.calcular(50, demanda = 0))
        assertEquals(StatusEstoque.DISPONIVEL, regra.calcular(50, demanda = null))
    }

    @Test
    fun `demanda nao tira o item de falta`() {
        assertEquals(StatusEstoque.EM_FALTA, regra.calcular(0, demanda = 100))
    }

    @Test
    fun `limite zero so reserva por demanda`() {
        val semLimite = RegraStatusEstoque(EstoqueProperties(limiteReservado = 0))

        assertEquals(StatusEstoque.DISPONIVEL, semLimite.calcular(1))
        assertEquals(StatusEstoque.RESERVADO, semLimite.calcular(1, demanda = 5))
        assertEquals(StatusEstoque.EM_FALTA, semLimite.calcular(0))
    }

    @Test
    fun `limite negativo e rejeitado`() {
        assertFailsWith<IllegalArgumentException> { EstoqueProperties(limiteReservado = -1) }
    }

    @Test
    fun `limite vem do application yml e tem valor padrao`() {
        val contexto = ApplicationContextRunner().withUserConfiguration(EstoqueConfig::class.java)

        contexto.run { assertEquals(20, it.getBean(EstoqueProperties::class.java).limiteReservado) }
        contexto.withPropertyValues("app.estoque.limite-reservado=5")
            .run { assertEquals(5, it.getBean(EstoqueProperties::class.java).limiteReservado) }
    }

    @Test
    fun `limite negativo na configuracao impede a aplicacao de subir`() {
        ApplicationContextRunner().withUserConfiguration(EstoqueConfig::class.java)
            .withPropertyValues("app.estoque.limite-reservado=-1")
            .run { assertNotNull(it.startupFailure) }
    }
}
