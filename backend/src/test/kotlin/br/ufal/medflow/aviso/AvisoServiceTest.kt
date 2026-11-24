package br.ufal.medflow.aviso

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ItemEstoque
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.remessa.EtapaRemessa
import br.ufal.medflow.remessa.Remessa
import br.ufal.medflow.remessa.RemessaEvento
import br.ufal.medflow.remessa.RemessaEventoRepository
import br.ufal.medflow.remessa.RemessaRepository
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Regras dos avisos, sem banco: estoque, remessas e sincronização com relógio fixo. */
class AvisoServiceTest {

    private val agora = Instant.parse("2026-11-23T12:00:00Z")
    private val ubsRepository = mock(UbsRepository::class.java)
    private val itens = mock(ItemEstoqueRepository::class.java)
    private val remessas = mock(RemessaRepository::class.java)
    private val eventos = mock(RemessaEventoRepository::class.java)
    private val service = AvisoService(ubsRepository, itens, remessas, eventos, Clock.fixed(agora, ZoneOffset.UTC))

    private fun ubs(ultimaSincronizacao: Instant?) = Ubs(
        cnes = "9000101", nome = "UBS Centro", tipo = TipoUnidade.UBS, bairro = "Centro", endereco = "Rua A, 1",
        codigoIbgeMunicipio = "2704302", latitude = -9.6, longitude = -35.7,
        ultimaSincronizacao = ultimaSincronizacao, id = 1L,
    ).also { given(ubsRepository.findById(1L)).willReturn(Optional.of(it)) }

    private fun med(id: Long, nome: String) =
        Medicamento(catmat = "BR$id", nome = nome, concentracao = "10 mg", formaFarmaceutica = "Comprimido",
            categoria = "Teste", id = id)

    private fun haHoras(h: Long): Instant = agora.minus(Duration.ofHours(h))

    @Test
    fun `ubs inexistente retorna 404`() {
        given(ubsRepository.findById(9L)).willReturn(Optional.empty())
        assertFailsWith<RecursoNaoEncontradoException> { service.listar(9L, setOf(1L)) }
    }

    @Test
    fun `sincronizacao atrasada so aparece depois de 24 h, com id por episodio`() {
        ubs(haHoras(24))
        assertTrue(service.listar(1L, emptySet()).isEmpty())

        ubs(haHoras(25))
        val aviso = service.listar(1L, emptySet()).single()
        assertEquals(TipoAviso.SINCRONIZACAO_ATRASADA, aviso.tipo)
        assertEquals("sincronizacao-1-${haHoras(25).epochSecond}", aviso.id)

        ubs(null)
        assertEquals("sincronizacao-1-nunca", service.listar(1L, emptySet()).single().id)
    }

    @Test
    fun `sem medicamentos, nao consulta estoque nem remessas`() {
        ubs(haHoras(1))
        assertTrue(service.listar(1L, emptySet()).isEmpty())
        verifyNoInteractions(itens, remessas, eventos)
    }

    @Test
    fun `disponivel so com status DISPONIVEL, quantidade e entre os medicamentos acompanhados`() {
        val unidade = ubs(haHoras(1))
        given(itens.findByUbs(1L)).willReturn(
            listOf(
                ItemEstoque(unidade, med(1, "Metformina"), StatusEstoque.DISPONIVEL, 30, haHoras(2)),
                ItemEstoque(unidade, med(2, "Losartana"), StatusEstoque.DISPONIVEL, 0, haHoras(2)),
                ItemEstoque(unidade, med(3, "Captopril"), StatusEstoque.RESERVADO, 9, haHoras(2)),
                ItemEstoque(unidade, med(4, "Insulina"), StatusEstoque.DISPONIVEL, 5, haHoras(2)),
            ),
        )
        given(remessas.findEmAndamentoDaUbs(1L)).willReturn(emptyList())

        val avisos = service.listar(1L, setOf(1L, 2L, 3L))

        assertEquals(listOf("disponivel-1-1"), avisos.map { it.id })
        assertEquals(30, avisos.single().quantidade)
        assertEquals("Metformina", avisos.single().medicamentoNome)
    }

    @Test
    fun `remessa usa a etapa mais avancada, ou PEDIDO sem eventos, e ordena do mais recente`() {
        val unidade = ubs(haHoras(1))
        given(itens.findByUbs(1L)).willReturn(emptyList())
        val emTransporte = Remessa(unidade, med(1, "Metformina"), 300, LocalDate.of(2026, 11, 25), haHoras(72), id = 10L)
        val semEventos = Remessa(unidade, med(2, "Losartana"), 40, LocalDate.of(2026, 11, 30), haHoras(5), id = 11L)
        val deOutroRemedio = Remessa(unidade, med(9, "Outro"), 1, LocalDate.of(2026, 11, 26), haHoras(1), id = 12L)
        given(remessas.findEmAndamentoDaUbs(1L)).willReturn(listOf(emTransporte, semEventos, deOutroRemedio))
        given(eventos.findDasRemessas(listOf(10L, 11L))).willReturn(
            listOf(
                RemessaEvento(emTransporte, EtapaRemessa.PEDIDO, haHoras(72)),
                RemessaEvento(emTransporte, EtapaRemessa.EM_TRANSPORTE, haHoras(3)),
                RemessaEvento(emTransporte, EtapaRemessa.SEPARADO_CAF, haHoras(30)),
            ),
        )

        val avisos = service.listar(1L, setOf(1L, 2L))

        assertEquals(listOf("remessa-10-EM_TRANSPORTE", "remessa-11-PEDIDO"), avisos.map { it.id })
        assertEquals(haHoras(3), avisos[0].ocorridoEm)
        assertEquals(LocalDate.of(2026, 11, 25), avisos[0].previsaoChegada)
        assertEquals(haHoras(5), avisos[1].ocorridoEm)
    }

    @Test
    fun `aviso sem data vai para o fim`() {
        val unidade = ubs(null)
        given(itens.findByUbs(1L)).willReturn(
            listOf(ItemEstoque(unidade, med(1, "Metformina"), StatusEstoque.DISPONIVEL, 30, haHoras(2))),
        )
        given(remessas.findEmAndamentoDaUbs(1L)).willReturn(emptyList())

        assertEquals(
            listOf(TipoAviso.DISPONIVEL, TipoAviso.SINCRONIZACAO_ATRASADA),
            service.listar(1L, setOf(1L)).map { it.tipo },
        )
    }
}
