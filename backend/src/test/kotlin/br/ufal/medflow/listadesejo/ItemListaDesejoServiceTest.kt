package br.ufal.medflow.listadesejo

import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** Testes do tratamento de violações de integridade ao criar um desejo. */
class ItemListaDesejoServiceTest {

    private val itens = mock(ItemListaDesejoRepository::class.java)
    private val unidades = mock(UbsRepository::class.java)
    private val medicamentos = mock(MedicamentoRepository::class.java)

    private val service = ItemListaDesejoService(
        itens,
        unidades,
        medicamentos,
    )

    private val aparelho = UUID.fromString("3f1c2b7e-8a4d-4e1f-9b2c-5d6e7f8a9b0c")

    private fun prepararCriacao() {
        val ubs = Ubs(
            cnes = "9000101",
            nome = "UBS Teste",
            tipo = TipoUnidade.UBS,
            bairro = "Centro",
            endereco = "Rua A, 1",
            codigoIbgeMunicipio = "2704302",
            latitude = -9.5,
            longitude = -35.7,
            id = 2L,
        )

        val medicamento = Medicamento(
            catmat = "BR0267690",
            nome = "Metformina",
            concentracao = "850 mg",
            formaFarmaceutica = "Comprimido",
            categoria = "Diabetes",
            id = 3L,
        )

        given(unidades.findById(2L)).willReturn(Optional.of(ubs))
        given(medicamentos.findById(3L)).willReturn(Optional.of(medicamento))
        given(itens.existsByDispositivoIdAndUbsIdAndMedicamentoId(aparelho, 2L, 3L))
            .willReturn(false)
    }

    private fun violacao(
        sqlState: String,
        constraint: String,
    ): DataIntegrityViolationException {
        val causa = ConstraintViolationException(
            "Violação de constraint",
            SQLException("Violação de integridade", sqlState),
            constraint,
        )

        return DataIntegrityViolationException("Falha ao inserir item", causa)
    }

    @Test
    fun `duplicata detectada pelo banco vira excecao de dominio`() {
        prepararCriacao()

        val erro = violacao(
            sqlState = "23505",
            constraint = "uk_item_lista_desejo",
        )

        given(itens.saveAndFlush(any(ItemListaDesejo::class.java)))
            .willThrow(erro)

        assertFailsWith<ItemListaDesejoDuplicadoException> {
            service.criar(
                aparelho,
                CriarItemListaDesejoRequest(
                    ubsId = 2L,
                    medicamentoId = 3L,
                ),
            )
        }
    }

    @Test
    fun `duplicata e detectada independentemente do nome da constraint`() {
        prepararCriacao()

        val erro = violacao(
            sqlState = "23505",
            constraint = "nome_diferente_informado_pelo_hibernate",
        )

        given(itens.saveAndFlush(any(ItemListaDesejo::class.java)))
            .willThrow(erro)

        assertFailsWith<ItemListaDesejoDuplicadoException> {
            service.criar(
                aparelho,
                CriarItemListaDesejoRequest(
                    ubsId = 2L,
                    medicamentoId = 3L,
                ),
            )
        }
    }

    @Test
    fun `outra violacao de integridade nao e tratada como duplicata`() {
        prepararCriacao()

        val erro = violacao(
            sqlState = "23503",
            constraint = "item_lista_desejo_ubs_id_fkey",
        )

        given(itens.saveAndFlush(any(ItemListaDesejo::class.java)))
            .willThrow(erro)

        val lancada = assertFailsWith<DataIntegrityViolationException> {
            service.criar(
                aparelho,
                CriarItemListaDesejoRequest(
                    ubsId = 2L,
                    medicamentoId = 3L,
                ),
            )
        }

        assertSame(erro, lancada)
    }
}
