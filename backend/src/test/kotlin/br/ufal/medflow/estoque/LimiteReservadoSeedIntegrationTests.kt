package br.ufal.medflow.estoque

import br.ufal.medflow.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class LimiteReservadoSeedIntegrationTests(
    @Autowired private val regra: RegraStatusEstoque,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @Test
    fun `seed define limite para metformina, losartana e insulina`() {
        val limites = jdbc.queryForList("SELECT catmat, limite_reservado FROM medicamento WHERE limite_reservado IS NOT NULL")
            .associate { it["catmat"] as String to it["limite_reservado"] as Int }

        assertEquals(50, limites["BR0267690"])
        assertEquals(30, limites["BR0268856"])
        assertEquals(10, limites["BR0271157"])
    }

    @Test
    fun `status dos itens do seed base com limite proprio e o que a regra daria`() {
        // itens de V1000 dos medicamentos que têm limite: (UBS, CATMAT, quantidade, status)
        val esperados = listOf(
            listOf("9000102", "BR0267690", 14, "RESERVADO"),
            listOf("9000104", "BR0267690", 85, "DISPONIVEL"),
            listOf("9000107", "BR0267690", 6, "RESERVADO"),
            listOf("9000104", "BR0268856", 10, "RESERVADO"),
            listOf("9000101", "BR0271157", 4, "RESERVADO"),
            listOf("9000103", "BR0271157", 18, "DISPONIVEL"),
        )

        esperados.forEach { (cnes, catmat, quantidade, status) ->
            val linha = jdbc.queryForMap(
                "SELECT i.quantidade, i.status, m.limite_reservado FROM item_estoque i " +
                    "JOIN ubs u ON u.id = i.ubs_id JOIN medicamento m ON m.id = i.medicamento_id WHERE u.cnes = ? AND m.catmat = ?",
                cnes,
                catmat,
            )
            assertEquals(quantidade, linha["quantidade"], "$cnes $catmat: quantidade")
            assertEquals(status, linha["status"], "$cnes $catmat: status")
            assertEquals(status, regra.calcular(quantidade as Int, limiteDoMedicamento = linha["limite_reservado"] as Int?).name, "$cnes $catmat: regra")
        }
    }
}
