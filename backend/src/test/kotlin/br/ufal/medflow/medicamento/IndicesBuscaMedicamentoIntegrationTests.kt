package br.ufal.medflow.medicamento

import br.ufal.medflow.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertTrue

/**
 * As buscas de GET /api/medicamentos usam os índices normalizados da V15 (PDS-51).
 * Com 20 mil linhas e estatísticas atualizadas, o planejador escolhe o índice sozinho (sem desligar o seq scan).
 * Os predicados correspondem aos utilizados na busca tolerante.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class IndicesBuscaMedicamentoIntegrationTests(@Autowired private val jdbc: JdbcTemplate) {

    @BeforeEach
    fun gerarVolume() {
        jdbc.update(
            """
            INSERT INTO medicamento (catmat, nome, concentracao, forma_farmaceutica, categoria)
            SELECT 'VOL' || n, 'Remédio ' || md5(n::TEXT), '10 mg', 'comprimido', 'Categoria ' || (n % 40)
            FROM generate_series(1, 20000) AS n
            """,
        )
        jdbc.update(
            "INSERT INTO medicamento (catmat, nome, concentracao, forma_farmaceutica, categoria) " +
                "VALUES ('VOL-MET', 'Metformina', '850 mg', 'comprimido', 'Diabetes')",
        )
        // Inserção em massa depois do índice GIN vai para a "pending list" (em produção o autovacuum a esvazia).
        jdbc.queryForList("SELECT gin_clean_pending_list('idx_medicamento_nome_busca_trgm')")
        jdbc.queryForList("SELECT gin_clean_pending_list('idx_medicamento_categoria_busca_trgm')")
        jdbc.execute("ANALYZE medicamento")
    }

    private fun plano(sql: String, vararg parametros: Any): String =
        jdbc.queryForList("EXPLAIN $sql", String::class.java, *parametros).joinToString("\n")

    @Test
    fun `busca por termo usa os indices de nome e categoria`() {
        val plano = plano(
            """
            SELECT * FROM medicamento m
            WHERE m.nome_busca LIKE '%' || ? || '%' OR m.categoria_busca LIKE '%' || ? || '%'
            ORDER BY m.nome, m.id LIMIT 20
            """,
            "metformina",
            "metformina",
        )

        assertTrue("idx_medicamento_nome_busca_trgm" in plano, plano)
        assertTrue("idx_medicamento_categoria_busca_trgm" in plano, plano)
        assertTrue("Seq Scan" !in plano, plano)
    }

    @Test
    fun `filtro exato por categoria usa o indice de categoria`() {
        val plano = plano(
            "SELECT * FROM medicamento m WHERE m.categoria_busca = lower(unaccent(?)) ORDER BY m.nome, m.id LIMIT 20",
            "diabetes",
        )

        assertTrue("idx_medicamento_categoria_busca_trgm" in plano, plano)
        assertTrue("Seq Scan" !in plano, plano)
    }

    @Test
    fun `busca por similaridade usa indice de nome normalizado`() {
        val plano = plano(
            """
            SELECT * FROM medicamento m
            WHERE m.nome_busca %> ?
            ORDER BY m.nome, m.id LIMIT 20
            """,
            "metiformina",
        )

        assertTrue("idx_medicamento_nome_busca_trgm" in plano, plano)
        assertTrue("Seq Scan" !in plano, plano)
    }

    @Test
    fun `consulta completa de relevancia aproveita indice normalizado`() {
        val consulta = MedicamentoRepository::class.java
            .getMethod(
                "buscarPorRelevancia",
                String::class.java,
                String::class.java,
                Pageable::class.java,
            )
            .getAnnotation(Query::class.java)
            .value

        val jdbcNomeado = NamedParameterJdbcTemplate(jdbc)

        val plano = jdbcNomeado.queryForList(
            "EXPLAIN (ANALYZE, BUFFERS) $consulta LIMIT 20",
            mapOf(
                "buscas" to "metiformina",
                "categoria" to "",
            ),
            String::class.java,
        ).joinToString("\n")

        println("PDS-51 - Plano da consulta completa:")
        println(plano)

        assertTrue(
            "idx_medicamento_nome_busca_trgm" in plano,
            plano,
        )
    }

}
