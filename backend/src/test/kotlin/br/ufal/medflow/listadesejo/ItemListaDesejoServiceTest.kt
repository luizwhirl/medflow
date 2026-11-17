package br.ufal.medflow.listadesejo

import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
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
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** Testes do tratamento de violações de integridade ao criar um desejo. */
class ItemListaDesejoServiceTest {

    private val itens = mock(ItemListaDesejoRepository::class.java)
    private val usuarios = mock(UsuarioRepository::class.java)
    private val unidades = mock(UbsRepository::class.java)
    private val medicamentos = mock(MedicamentoRepository::class.java)

    private val service = ItemListaDesejoService(
        itens,
        usuarios,
        unidades,
        medicamentos,
    )

    private fun prepararCriacao() {
        val usuario = Usuario(
            nome = "Cidadão Teste",
            email = "cidadao@teste.com",
            senhaHash = "hash-teste",
            papel = PapelUsuario.CIDADAO,
            id = 1L,
        )

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

        given(usuarios.findById(1L)).willReturn(Optional.of(usuario))
        given(unidades.findById(2L)).willReturn(Optional.of(ubs))
        given(medicamentos.findById(3L)).willReturn(Optional.of(medicamento))
        given(itens.existsByUsuarioIdAndUbsIdAndMedicamentoId(1L, 2L, 3L))
            .willReturn(false)
    }

    private fun violacao(constraint: String): DataIntegrityViolationException {
        val causa = ConstraintViolationException(
            "Violação de constraint",
            SQLException("Violação de integridade", "23505"),
            constraint,
        )

        return DataIntegrityViolationException("Falha ao inserir item", causa)
    }

    @Test
    fun `duplicata detectada pelo banco vira excecao de dominio`() {
        prepararCriacao()

        given(itens.saveAndFlush(any(ItemListaDesejo::class.java)))
            .willThrow(violacao("uk_item_lista_desejo"))

        assertFailsWith<ItemListaDesejoDuplicadoException> {
            service.criar(
                1L,
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

        val erro = violacao("item_lista_desejo_ubs_id_fkey")

        given(itens.saveAndFlush(any(ItemListaDesejo::class.java)))
            .willThrow(erro)

        val lancada = assertFailsWith<DataIntegrityViolationException> {
            service.criar(
                1L,
                CriarItemListaDesejoRequest(
                    ubsId = 2L,
                    medicamentoId = 3L,
                ),
            )
        }

        assertSame(erro, lancada)
    }
}
