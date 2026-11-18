package br.ufal.medflow.listadesejo

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.auth.PapelUsuario
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

/** Verifica a exclusão em cascata definida na migration V5.1. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ItemListaDesejoCascadeIntegrationTests(
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val ubsRepository: UbsRepository,
    @Autowired private val medicamentos: MedicamentoRepository,
    @Autowired private val itens: ItemListaDesejoRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private fun criarCidadao(nome: String, email: String): Usuario =
        usuarios.saveAndFlush(
            Usuario(
                nome = nome,
                email = email,
                senhaHash = "hash-teste",
                papel = PapelUsuario.CIDADAO,
            ),
        )

    private fun criarUbs(): Ubs =
        ubsRepository.saveAndFlush(
            Ubs(
                cnes = "9000101",
                nome = "UBS Teste",
                tipo = TipoUnidade.UBS,
                bairro = "Centro",
                endereco = "Rua A, 1",
                codigoIbgeMunicipio = "2704302",
                latitude = -9.5,
                longitude = -35.7,
            ),
        )

    private fun criarMedicamento(): Medicamento =
        medicamentos.saveAndFlush(
            Medicamento(
                catmat = "BR0267690",
                nome = "Metformina",
                concentracao = "850 mg",
                formaFarmaceutica = "Comprimido",
                categoria = "Diabetes",
            ),
        )

    private fun quantidadeDeDesejos(usuarioId: Long): Long =
        requireNotNull(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_lista_desejo WHERE usuario_id = ?",
                Long::class.java,
                usuarioId,
            ),
        )

    @Test
    fun `excluir usuario remove seus desejos sem afetar outros usuarios`() {
        val rita = criarCidadao(
            nome = "Rita",
            email = "rita-cascade@teste.com",
        )

        val carlos = criarCidadao(
            nome = "Carlos",
            email = "carlos-cascade@teste.com",
        )

        val ubs = criarUbs()
        val medicamento = criarMedicamento()

        itens.saveAndFlush(
            ItemListaDesejo(
                usuario = rita,
                ubs = ubs,
                medicamento = medicamento,
            ),
        )

        itens.saveAndFlush(
            ItemListaDesejo(
                usuario = carlos,
                ubs = ubs,
                medicamento = medicamento,
            ),
        )

        val ritaId = requireNotNull(rita.id)
        val carlosId = requireNotNull(carlos.id)

        // Ambos possuem um item antes da exclusão.
        assertEquals(1L, quantidadeDeDesejos(ritaId))
        assertEquals(1L, quantidadeDeDesejos(carlosId))

        // Executa diretamente no banco para testar a FK ON DELETE CASCADE.
        val linhasRemovidas = jdbc.update(
            "DELETE FROM usuario WHERE id = ?",
            ritaId,
        )

        assertEquals(1, linhasRemovidas)

        // O PostgreSQL deve apagar automaticamente os desejos da Rita.
        assertEquals(0L, quantidadeDeDesejos(ritaId))

        // Os desejos do Carlos devem permanecer intactos.
        assertEquals(1L, quantidadeDeDesejos(carlosId))
    }
}
