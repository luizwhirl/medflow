package br.ufal.medflow.gestao

import br.ufal.medflow.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import kotlin.test.assertTrue

/**
 * Em vez de medir milissegundos (que oscilam no CI), confere o plano do PostgreSQL: com volume, as consultas do painel
 * usam os índices em vez de varrer as tabelas. Perfil "test" (sem seed): o volume é gerado aqui.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PainelGestaoPlanoIntegrationTests(@Autowired private val jdbc: JdbcTemplate) {

    @BeforeEach
    fun gerarVolume() {
        // 200 UBS x 400 medicamentos = 80 mil itens, um lote por item (vencimentos espalhados por ~5 anos) e 40 mil remessas
        jdbc.update(
            """
            INSERT INTO ubs (cnes, nome, tipo, bairro, endereco, codigo_ibge_municipio, latitude, longitude)
            SELECT 'V' || LPAD(n::TEXT, 6, '0'), 'UBS ' || n, 'UBS', 'Centro', 'Rua A, 1', '9999999', -9.5, -35.7
            FROM generate_series(1, 200) AS n
            """,
        )
        jdbc.update(
            """
            INSERT INTO medicamento (catmat, nome, concentracao, forma_farmaceutica, categoria)
            SELECT 'PV' || n, 'Remédio ' || n, '10 mg', 'comprimido', 'Teste' FROM generate_series(1, 400) AS n
            """,
        )
        jdbc.update(
            """
            INSERT INTO item_estoque (ubs_id, medicamento_id, status, quantidade)
            SELECT u.id, m.id, 'DISPONIVEL', 100 FROM ubs u CROSS JOIN medicamento m
            """,
        )
        jdbc.update(
            """
            INSERT INTO lote_estoque (item_estoque_id, numero_lote, validade, quantidade)
            SELECT i.id, 'L' || i.id, CURRENT_DATE + (i.id % 1800)::INTEGER, 10 FROM item_estoque i
            """,
        )
        jdbc.update(
            """
            INSERT INTO remessa (ubs_id, medicamento_id, quantidade, previsao_chegada)
            SELECT i.ubs_id, i.medicamento_id, 10, CURRENT_DATE + 5 FROM item_estoque i WHERE i.id % 2 = 0
            """,
        )
        listOf("ubs", "medicamento", "item_estoque", "lote_estoque", "remessa").forEach { jdbc.execute("ANALYZE $it") }
    }

    private fun plano(sql: String, vararg parametros: Any): String =
        jdbc.queryForList("EXPLAIN $sql", String::class.java, *parametros).joinToString("\n")

    private fun ubsDeTeste() = jdbc.queryForObject("SELECT MIN(id) FROM ubs", Long::class.java)!!

    @Test
    fun `lotes vencendo usam o indice de validade`() {
        val plano = plano(
            """
            SELECT COUNT(*) FROM lote_estoque l JOIN item_estoque i ON i.id = l.item_estoque_id
            WHERE l.quantidade > 0 AND l.validade <= ?
            """,
            LocalDate.now().plusDays(60),
        )

        assertTrue("idx_lote_estoque_validade" in plano, plano)
        assertTrue("Seq Scan on lote_estoque" !in plano, plano)
    }

    @Test
    fun `remessas de uma UBS usam o indice da UBS e da previsao`() {
        val plano = plano("SELECT COUNT(*) FROM remessa r WHERE r.ubs_id = ?", ubsDeTeste())

        assertTrue("idx_remessa_ubs" in plano, plano)
        assertTrue("Seq Scan on remessa" !in plano, plano)
    }

    @Test
    fun `itens de uma UBS usam o indice da chave unica`() {
        val plano = plano(
            "SELECT COUNT(*) FILTER (WHERE i.quantidade <= 0) FROM item_estoque i WHERE i.ubs_id = ?",
            ubsDeTeste(),
        )

        assertTrue("uk_item_estoque" in plano, plano)
        assertTrue("Seq Scan on item_estoque" !in plano, plano)
    }
}
