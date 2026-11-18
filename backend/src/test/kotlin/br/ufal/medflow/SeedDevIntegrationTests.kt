package br.ufal.medflow

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Perfil "dev": o seed precisa ter dados para todas as telas (PDS-17). */
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class SeedDevIntegrationTests(@Autowired private val jdbc: JdbcTemplate) {

    private fun contar(sql: String): Int = jdbc.queryForObject(sql, Int::class.java)!!

    @Test
    fun `todo medicamento tem posicao de estoque em todas as UBS`() {
        val semPosicao = contar(
            """
            SELECT COUNT(*) FROM ubs u CROSS JOIN medicamento m
            WHERE u.cnes LIKE '90001%'
              AND NOT EXISTS (SELECT 1 FROM item_estoque i WHERE i.ubs_id = u.id AND i.medicamento_id = m.id)
            """,
        )

        assertEquals(0, semPosicao)
    }

    @Test
    fun `status do seed segue a regra de estoque`() {
        // Regra de RegraStatusEstoque com app.estoque.limite-reservado = 20
        val divergentes = jdbc.queryForList(
            """
            SELECT u.cnes || '/' || m.catmat FROM item_estoque i
            JOIN ubs u ON u.id = i.ubs_id JOIN medicamento m ON m.id = i.medicamento_id
            WHERE i.status <> CASE WHEN i.quantidade = 0 THEN 'EM_FALTA'
                                   WHEN i.quantidade <= 20 THEN 'RESERVADO'
                                   ELSE 'DISPONIVEL' END
            """,
            String::class.java,
        )

        // Exceção conhecida do V1000 (já mergeado): Insulina na UBS Clima Bom com 18 unidades como DISPONIVEL.
        assertEquals(listOf("9000103/BR0271157"), divergentes)
    }

    @Test
    fun `cada status aparece em todas as UBS`() {
        val ubsSemTodosOsStatus = contar(
            "SELECT COUNT(*) FROM (SELECT ubs_id FROM item_estoque GROUP BY ubs_id HAVING COUNT(DISTINCT status) < 3) s",
        )

        assertEquals(0, ubsSemTodosOsStatus)
    }

    @Test
    fun `ha lotes vencendo em ate 60 dias em varias UBS`() {
        val ubsComLoteVencendo = contar(
            """
            SELECT COUNT(DISTINCT i.ubs_id) FROM lote_estoque l JOIN item_estoque i ON i.id = l.item_estoque_id
            WHERE l.validade <= CURRENT_DATE + 60 AND l.quantidade > 0
            """,
        )

        assertTrue(ubsComLoteVencendo >= 5, "UBS com lote vencendo: $ubsComLoteVencendo")
    }

    @Test
    fun `so a USF Riacho Doce esta com a sincronizacao atrasada`() {
        val atrasadas = jdbc.queryForList(
            "SELECT cnes FROM ubs WHERE ultima_sincronizacao < NOW() - INTERVAL '24 hours'",
            String::class.java,
        )

        assertEquals(listOf("9000107"), atrasadas)
    }
}
