package br.ufal.medflow

import br.ufal.medflow.estoque.RegraStatusEstoque
import br.ufal.medflow.remessa.EtapaRemessa
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
class SeedDevIntegrationTests(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val regra: RegraStatusEstoque,
) {

    private fun contar(sql: String): Int = jdbc.queryForObject(sql, Int::class.java)!!

    /** Medicamentos fictícios (V1000 e V1101). A sincronização da BNAFAR pode criar outros no perfil dev. */
    private val catmatsFicticios = listOf(
        "BR0267690", "BR0267612", "BR0268856", "BR0271157", "BR0267620",
        "BR0267515", "BR0268280", "BR0269919", "BR0267543", "BR0268338",
        "BR0272301", "BR0272302", "BR0272303", "BR0272304", "BR0272305", "BR0272306", "BR0272307",
    ).joinToString(",") { "'$it'" }

    @Test
    fun `todo medicamento ficticio tem posicao de estoque em todas as UBS`() {
        val semPosicao = contar(
            """
            SELECT COUNT(*) FROM ubs u CROSS JOIN medicamento m
            WHERE u.cnes LIKE '90001%' AND m.catmat IN ($catmatsFicticios)
              AND NOT EXISTS (SELECT 1 FROM item_estoque i WHERE i.ubs_id = u.id AND i.medicamento_id = m.id)
            """,
        )

        assertEquals(0, semPosicao)
    }

    @Test
    fun `seed tem os 17 medicamentos ficticios em 7 categorias`() {
        assertEquals(17, contar("SELECT COUNT(*) FROM medicamento WHERE catmat IN ($catmatsFicticios)"))
        assertEquals(7, contar("SELECT COUNT(DISTINCT categoria) FROM medicamento WHERE catmat IN ($catmatsFicticios)"))
    }

    @Test
    fun `status do seed segue a regra de estoque`() {
        // Com o limite de reservado do medicamento, quando ele tem um (V1008), ou o global
        val divergentes = jdbc.queryForList(
            """
            SELECT u.cnes, m.catmat, i.quantidade, i.status, m.limite_reservado FROM item_estoque i
            JOIN ubs u ON u.id = i.ubs_id JOIN medicamento m ON m.id = i.medicamento_id
            WHERE m.catmat IN ($catmatsFicticios)
            """,
        ).filter {
            val esperado = regra.calcular(it["quantidade"] as Int, limiteDoMedicamento = it["limite_reservado"] as Int?)
            it["status"] != esperado.name
        }.map { "${it["cnes"]}/${it["catmat"]}" }

        assertTrue(divergentes.isEmpty(), "itens com status diferente da regra: $divergentes")
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
    fun `ha remessa parada em cada etapa`() {
        // A etapa atual de uma remessa é a mais avançada dos eventos (V6__remessas.sql)
        val etapasAtuais = jdbc.queryForList(
            """
            SELECT DISTINCT (ARRAY['PEDIDO', 'SEPARADO_CAF', 'EM_TRANSPORTE', 'RECEBIDO', 'DISPONIVEL'])[
                MAX(array_position(ARRAY['PEDIDO', 'SEPARADO_CAF', 'EM_TRANSPORTE', 'RECEBIDO', 'DISPONIVEL'], e.etapa::TEXT))
            ]
            FROM remessa_evento e GROUP BY e.remessa_id
            """,
            String::class.java,
        ).toSet()

        assertEquals(EtapaRemessa.entries.map { it.name }.toSet(), etapasAtuais)
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
