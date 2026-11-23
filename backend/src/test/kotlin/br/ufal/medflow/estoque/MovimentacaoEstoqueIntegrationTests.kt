package br.ufal.medflow.estoque

import br.ufal.medflow.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MovimentacaoEstoqueIntegrationTests(
    @Autowired private val movimentacoes: MovimentacaoEstoqueRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private fun idUbs(cnes: String) = jdbc.queryForObject("SELECT id FROM ubs WHERE cnes = ?", Long::class.java, cnes)!!

    private fun idItem(cnes: String, catmat: String) = jdbc.queryForObject(
        "SELECT i.id FROM item_estoque i JOIN ubs u ON u.id = i.ubs_id JOIN medicamento m ON m.id = i.medicamento_id WHERE u.cnes = ? AND m.catmat = ?",
        Long::class.java,
        cnes,
        catmat,
    )!!

    private fun inserir(tipo: String, quantidade: Int) = jdbc.update(
        "INSERT INTO movimentacao_estoque (item_estoque_id, tipo, quantidade) VALUES (?, ?, ?)",
        idItem("9000101", "BR0267690"),
        tipo,
        quantidade,
    )

    private fun contar(item: Long) =
        jdbc.queryForObject("SELECT COUNT(*) FROM movimentacao_estoque WHERE item_estoque_id = ?", Int::class.java, item)!!

    @Test
    fun `seed tem movimentacoes dos tres tipos`() {
        val tipos = jdbc.queryForList("SELECT DISTINCT tipo FROM movimentacao_estoque", String::class.java).toSet()

        assertEquals(setOf("ENTRADA", "DISPENSACAO", "AJUSTE"), tipos)
    }

    @Test
    fun `soma das movimentacoes de cada item do seed bate com a quantidade em estoque`() {
        val divergentes = jdbc.queryForList(
            """
            SELECT i.id FROM item_estoque i
            JOIN movimentacao_estoque m ON m.item_estoque_id = i.id
            GROUP BY i.id, i.quantidade HAVING SUM(m.quantidade) <> i.quantidade
            """,
            Long::class.java,
        )
        assertTrue(divergentes.isEmpty(), "itens cuja soma de movimentações difere da quantidade: $divergentes")
    }

    @Test
    fun `lista as movimentacoes da UBS dos ultimos 10 dias da mais recente para a mais antiga`() {
        val lista = movimentacoes.findDaUbsDesde(idUbs("9000101"), Instant.now().minus(10, ChronoUnit.DAYS))

        // dia 1 Losartana, dia 5 Losartana, dia 6 Dipirona, dia 7 Glibenclamida, dia 9 Losartana
        assertEquals(
            listOf(
                Triple("Losartana potássica", TipoMovimentacao.DISPENSACAO, -20),
                Triple("Losartana potássica", TipoMovimentacao.AJUSTE, 10),
                Triple("Dipirona", TipoMovimentacao.DISPENSACAO, -200),
                Triple("Glibenclamida", TipoMovimentacao.DISPENSACAO, -60),
                Triple("Losartana potássica", TipoMovimentacao.DISPENSACAO, -30),
            ),
            lista.map { Triple(it.itemEstoque.medicamento.nome, it.tipo, it.quantidade) },
        )
    }

    @Test
    fun `movimentacoes de outra UBS nao entram`() {
        val lista = movimentacoes.findDaUbsDesde(idUbs("9000104"), Instant.now().minus(30, ChronoUnit.DAYS))

        // dia 2 (-45), dia 10 (-70), dia 20 (+200)
        assertEquals(listOf(-45, -70, 200), lista.map { it.quantidade })
    }

    @Test
    fun `entrada negativa e rejeitada`() {
        assertFailsWith<DataIntegrityViolationException> { inserir("ENTRADA", -5) }
    }

    @Test
    fun `dispensacao positiva e rejeitada`() {
        assertFailsWith<DataIntegrityViolationException> { inserir("DISPENSACAO", 5) }
    }

    @Test
    fun `ajuste zerado e rejeitado`() {
        assertFailsWith<DataIntegrityViolationException> { inserir("AJUSTE", 0) }
    }

    @Test
    fun `tipo fora da lista e rejeitado`() {
        assertFailsWith<DataIntegrityViolationException> { inserir("PERDA", -5) }
    }

    @Test
    fun `ajuste aceita sinal positivo e negativo`() {
        assertEquals(1, inserir("AJUSTE", 3))
        assertEquals(1, inserir("AJUSTE", -3))
    }

    @Test
    fun `item com movimentacoes nao pode ser apagado`() {
        val item = idItem("9000103", "BR0267690")
        assertTrue(contar(item) > 0, "o seed precisa ter movimentações nesse item")

        assertFailsWith<DataIntegrityViolationException> {
            jdbc.update("DELETE FROM item_estoque WHERE id = ?", item)
        }
    }

    @Test
    fun `item sem movimentacoes pode ser apagado`() {
        val item = jdbc.queryForObject(
            "SELECT i.id FROM item_estoque i WHERE NOT EXISTS (SELECT 1 FROM movimentacao_estoque m WHERE m.item_estoque_id = i.id) LIMIT 1",
            Long::class.java,
        )!!

        assertEquals(1, jdbc.update("DELETE FROM item_estoque WHERE id = ?", item))
    }
}
