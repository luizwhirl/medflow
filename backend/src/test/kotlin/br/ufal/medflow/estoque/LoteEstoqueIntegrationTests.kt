package br.ufal.medflow.estoque

import br.ufal.medflow.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class LoteEstoqueIntegrationTests(
    @Autowired private val loteRepository: LoteEstoqueRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @Test
    fun `soma dos lotes bate com a quantidade de cada item do seed`() {
        val divergentes = jdbc.queryForList(
            """
            SELECT i.id FROM item_estoque i
            WHERE i.quantidade <> COALESCE((SELECT SUM(l.quantidade) FROM lote_estoque l WHERE l.item_estoque_id = i.id), 0)
            """,
            Long::class.java,
        )

        assertTrue(divergentes.isEmpty(), "itens com soma de lotes diferente da quantidade: $divergentes")
    }

    @Test
    fun `lista os lotes que vencem em ate 60 dias do que vence primeiro`() {
        val lotes = loteRepository.findComValidadeAte(LocalDate.now().plusDays(60))

        assertEquals(listOf("AMX-2601", "LOS-2602", "DIP-2603"), lotes.map { it.numeroLote })
        assertEquals("UBS Cidade Universitária", lotes.first().itemEstoque.ubs.nome)
    }

    @Test
    @Transactional
    fun `lote vencido sem saldo nao entra na lista`() {
        jdbc.update(
            """
            INSERT INTO lote_estoque (item_estoque_id, numero_lote, validade, quantidade)
            VALUES ((SELECT MIN(id) FROM item_estoque), 'VENCIDO-0', CURRENT_DATE - 1, 0)
            """,
        )

        val lotes = loteRepository.findComValidadeAte(LocalDate.now().plusDays(60))

        assertTrue(lotes.none { it.numeroLote == "VENCIDO-0" })
    }
}
