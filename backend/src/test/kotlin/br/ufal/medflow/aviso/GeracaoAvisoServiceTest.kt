package br.ufal.medflow.aviso

import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.listadesejo.ItemListaDesejoRepository
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Testes da geração de avisos de disponibilidade. */
class GeracaoAvisoServiceTest {

    private val listaDesejos =
        mock(ItemListaDesejoRepository::class.java)

    private val avisos =
        mock(AvisoRepository::class.java)

    private val instante =
        Instant.parse("2026-11-19T20:00:00Z")

    private val relogio =
        Clock.fixed(instante, ZoneOffset.UTC)

    private val service = GeracaoAvisoService(
        listaDesejos,
        avisos,
        relogio,
    )

    private val ubs = Ubs(
        cnes = "9000101",
        nome = "UBS Centro",
        tipo = TipoUnidade.UBS,
        bairro = "Centro",
        endereco = "Rua A, 1",
        codigoIbgeMunicipio = "2704302",
        latitude = -9.5,
        longitude = -35.7,
        id = 10L,
    )

    private val medicamento = Medicamento(
        catmat = "BR0267690",
        nome = "Metformina",
        concentracao = "850 mg",
        formaFarmaceutica = "Comprimido",
        categoria = "Diabetes",
        id = 20L,
    )

    private fun cidadao(id: Long): Usuario =
        Usuario(
            nome = "Cidadão $id",
            email = "cidadao-$id@teste.com",
            senhaHash = "hash-teste",
            papel = PapelUsuario.CIDADAO,
            id = id,
        )

    /**
     * Recupera os lotes de avisos enviados ao repositório.
     *
     * Usa as invocações registradas pelo Mockito para evitar
     * o null retornado por ArgumentCaptor.capture().
     */
    private fun capturarLotes(quantidade: Int): List<List<Aviso>> {
        val lotes = mockingDetails(avisos).invocations
            .filter { it.method.name == "saveAll" }
            .map { invocacao ->
                val enviados = invocacao.arguments.single() as Iterable<*>
                enviados.map { it as Aviso }
            }

        assertEquals(
            quantidade,
            lotes.size,
            "Quantidade inesperada de chamadas ao saveAll",
        )

        return lotes
    }

    @Test
    fun `gera aviso para cada cidadao interessado`() {
        val primeiro = cidadao(1L)
        val segundo = cidadao(2L)

        given(
            listaDesejos.buscarUsuariosParaDisponibilidade(10L, 20L),
        ).willReturn(listOf(primeiro, segundo))

        val quantidade = service.registrarDisponibilidade(
            ubs,
            medicamento,
        )

        assertEquals(2, quantidade)

        val criados = capturarLotes(1).single()

        assertEquals(2, criados.size)
        assertEquals(
            setOf(1L, 2L),
            criados.map { it.usuario.id }.toSet(),
        )

        criados.forEach { aviso ->
            assertEquals(
                TipoAviso.MEDICAMENTO_DISPONIVEL,
                aviso.tipo,
            )
            assertEquals(
                "Medicamento disponível",
                aviso.titulo,
            )
            assertTrue(aviso.texto.contains("Metformina"))
            assertTrue(aviso.texto.contains("UBS Centro"))
            assertFalse(aviso.lido)
            assertEquals(instante, aviso.criadoEm)
        }

        // Todos os destinatários compartilham a chave
        // da mesma transição de disponibilidade.
        assertEquals(
            criados[0].chaveEvento,
            criados[1].chaveEvento,
        )

        assertTrue(
            criados[0].chaveEvento.startsWith(
                "disponibilidade:10:20:",
            ),
        )
    }

    @Test
    fun `nao gera avisos sem cidadaos interessados`() {
        given(
            listaDesejos.buscarUsuariosParaDisponibilidade(10L, 20L),
        ).willReturn(emptyList())

        val quantidade = service.registrarDisponibilidade(
            ubs,
            medicamento,
        )

        assertEquals(0, quantidade)
        verifyNoInteractions(avisos)
    }

    @Test
    fun `gera chave diferente para novas transicoes`() {
        val usuario = cidadao(1L)

        given(
            listaDesejos.buscarUsuariosParaDisponibilidade(10L, 20L),
        ).willReturn(listOf(usuario))

        val primeira = service.registrarDisponibilidade(
            ubs,
            medicamento,
        )

        val segunda = service.registrarDisponibilidade(
            ubs,
            medicamento,
        )

        assertEquals(1, primeira)
        assertEquals(1, segunda)

        val lotes = capturarLotes(2)

        val primeiraChave = lotes[0].single().chaveEvento
        val segundaChave = lotes[1].single().chaveEvento

        assertNotEquals(primeiraChave, segundaChave)
    }

    @Test
    fun `consulta destinatarios pela ubs e medicamento corretos`() {
        given(
            listaDesejos.buscarUsuariosParaDisponibilidade(10L, 20L),
        ).willReturn(listOf(cidadao(1L)))

        service.registrarDisponibilidade(
            ubs,
            medicamento,
        )

        verify(listaDesejos)
            .buscarUsuariosParaDisponibilidade(10L, 20L)

        assertEquals(1, capturarLotes(1).single().size)
    }

    @Test
    fun `rejeita ubs que ainda nao foi persistida`() {
        val ubsSemId = Ubs(
            cnes = "9000102",
            nome = "UBS Nova",
            tipo = TipoUnidade.UBS,
            bairro = "Centro",
            endereco = "Rua B, 2",
            codigoIbgeMunicipio = "2704302",
            latitude = -9.5,
            longitude = -35.7,
        )

        assertFailsWith<IllegalArgumentException> {
            service.registrarDisponibilidade(
                ubsSemId,
                medicamento,
            )
        }

        verifyNoInteractions(listaDesejos, avisos)
    }
}
