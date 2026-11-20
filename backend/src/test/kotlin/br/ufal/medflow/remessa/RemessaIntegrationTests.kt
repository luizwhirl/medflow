package br.ufal.medflow.remessa

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
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class RemessaIntegrationTests(
    @Autowired private val remessas: RemessaRepository,
    @Autowired private val eventos: RemessaEventoRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private fun idUbs(cnes: String) = jdbc.queryForObject("SELECT id FROM ubs WHERE cnes = ?", Long::class.java, cnes)!!

    private fun idMedicamento(catmat: String) =
        jdbc.queryForObject("SELECT id FROM medicamento WHERE catmat = ?", Long::class.java, catmat)!!

    private fun inserirRemessa(cnes: String, catmat: String, quantidade: Int, previsao: LocalDate) =
        jdbc.queryForObject(
            "INSERT INTO remessa (ubs_id, medicamento_id, quantidade, previsao_chegada) VALUES (?, ?, ?, ?) RETURNING id",
            Long::class.java,
            idUbs(cnes),
            idMedicamento(catmat),
            quantidade,
            previsao,
        )!!

    @Test
    fun `toda remessa do seed comeca no pedido`() {
        assertTrue(remessas.count() > 0, "o seed precisa ter remessas")
        val semPedido = jdbc.queryForList(
            "SELECT r.id FROM remessa r WHERE NOT EXISTS (SELECT 1 FROM remessa_evento e WHERE e.remessa_id = r.id AND e.etapa = 'PEDIDO')",
            Long::class.java,
        )
        assertTrue(semPedido.isEmpty(), "remessas sem a etapa PEDIDO: $semPedido")
    }

    @Test
    fun `etapas de cada remessa do seed formam uma sequencia sem buracos`() {
        // a etapa mais avançada (1 a 5) tem que ser igual à quantidade de eventos
        val buracos = jdbc.queryForList(
            """
            SELECT remessa_id FROM remessa_evento GROUP BY remessa_id
            HAVING COUNT(*) <> MAX(CASE etapa WHEN 'PEDIDO' THEN 1 WHEN 'SEPARADO_CAF' THEN 2 WHEN 'EM_TRANSPORTE' THEN 3
                                              WHEN 'RECEBIDO' THEN 4 WHEN 'DISPONIVEL' THEN 5 END)
            """,
            Long::class.java,
        )
        assertTrue(buracos.isEmpty(), "remessas com etapa faltando: $buracos")
    }

    @Test
    fun `lista as remessas em andamento da UBS com o medicamento`() {
        val metformina = remessas.findEmAndamentoDaUbs(idUbs("9000101")).single { it.medicamento.nome == "Metformina" }

        assertEquals(300, metformina.quantidade)
        assertEquals(LocalDate.now().plusDays(2), metformina.previsaoChegada)
    }

    @Test
    fun `remessa que ja esta disponivel nao aparece em andamento`() {
        // a UBS 9000104 tem a remessa de Losartana, já na etapa DISPONIVEL
        val ubs = idUbs("9000104")
        val disponiveis = jdbc.queryForObject(
            "SELECT COUNT(*) FROM remessa r JOIN remessa_evento e ON e.remessa_id = r.id AND e.etapa = 'DISPONIVEL' WHERE r.ubs_id = ?",
            Int::class.java,
            ubs,
        )!!
        assertTrue(disponiveis >= 1, "o seed precisa ter uma remessa concluída na UBS 9000104")
        assertTrue(remessas.findEmAndamentoDaUbs(ubs).none { it.medicamento.nome == "Losartana potássica" })
    }

    @Test
    fun `em andamento vem na ordem da previsao de chegada`() {
        // seed: Metformina chega em 2 dias; as novas chegam antes (hoje) e depois (30 dias)
        inserirRemessa("9000101", "BR0268856", 10, LocalDate.now().plusDays(30))
        inserirRemessa("9000101", "BR0267543", 20, LocalDate.now())

        val nomes = setOf("Dipirona", "Metformina", "Losartana potássica")
        val lista = remessas.findEmAndamentoDaUbs(idUbs("9000101")).map { it.medicamento.nome }.filter { it in nomes }

        assertEquals(listOf("Dipirona", "Metformina", "Losartana potássica"), lista)
    }

    @Test
    fun `eventos da remessa vem em ordem e nenhum esta no futuro`() {
        val remessa = remessas.findEmAndamentoDaUbs(idUbs("9000101")).single { it.medicamento.nome == "Metformina" }

        val lista = eventos.findDasRemessas(listOf(requireNotNull(remessa.id)))

        assertEquals(listOf(EtapaRemessa.PEDIDO, EtapaRemessa.SEPARADO_CAF, EtapaRemessa.EM_TRANSPORTE), lista.map { it.etapa })
        assertEquals(lista.map { it.ocorridoEm }.sorted(), lista.map { it.ocorridoEm })
        assertTrue(lista.all { it.ocorridoEm <= Instant.now() })
    }

    @Test
    fun `quantidade zero e rejeitada`() {
        assertFailsWith<DataIntegrityViolationException> {
            inserirRemessa("9000101", "BR0268856", 0, LocalDate.now())
        }
    }

    @Test
    fun `etapa fora da lista e rejeitada`() {
        val id = inserirRemessa("9000101", "BR0268856", 5, LocalDate.now())

        assertFailsWith<DataIntegrityViolationException> {
            jdbc.update("INSERT INTO remessa_evento (remessa_id, etapa, ocorrido_em) VALUES (?, 'PERDIDA', NOW())", id)
        }
    }

    @Test
    fun `mesma etapa duas vezes na mesma remessa e rejeitada`() {
        val id = inserirRemessa("9000101", "BR0268856", 5, LocalDate.now())
        jdbc.update("INSERT INTO remessa_evento (remessa_id, etapa, ocorrido_em) VALUES (?, 'PEDIDO', NOW())", id)

        assertFailsWith<DataIntegrityViolationException> {
            jdbc.update("INSERT INTO remessa_evento (remessa_id, etapa, ocorrido_em) VALUES (?, 'PEDIDO', NOW())", id)
        }
    }

    @Test
    fun `apagar a remessa apaga os eventos`() {
        val remessa = remessas.findEmAndamentoDaUbs(idUbs("9000101")).single { it.medicamento.nome == "Metformina" }
        val id = requireNotNull(remessa.id)
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM remessa_evento WHERE remessa_id = ?", Int::class.java, id))

        jdbc.update("DELETE FROM remessa WHERE id = ?", id)

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM remessa_evento WHERE remessa_id = ?", Int::class.java, id))
    }
}
