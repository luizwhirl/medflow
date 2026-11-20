package br.ufal.medflow.aviso

import br.ufal.medflow.listadesejo.ItemListaDesejoRepository
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.ubs.Ubs
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Gera avisos a partir dos eventos da sincronização de estoque. */
@Service
class GeracaoAvisoService(
    private val listaDesejos: ItemListaDesejoRepository,
    private val avisos: AvisoRepository,
    private val relogio: Clock,
) {

    /**
     * Avisa os cidadãos interessados quando um medicamento
     * passa a estar disponível em uma UBS.
     *
     * Deve ser chamado apenas quando houver uma transição real
     * para DISPONIVEL, inclusive na criação de um item disponível.
     *
     * Participa obrigatoriamente da transação de sincronização:
     * se a atualização do estoque falhar, os avisos também
     * serão revertidos.
     *
     * Retorna a quantidade de avisos criados.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun registrarDisponibilidade(
        ubs: Ubs,
        medicamento: Medicamento,
    ): Int {
        val ubsId = requireNotNull(ubs.id) {
            "A UBS precisa estar persistida"
        }

        val medicamentoId = requireNotNull(medicamento.id) {
            "O medicamento precisa estar persistido"
        }

        val usuarios = listaDesejos.buscarUsuariosParaDisponibilidade(
            ubsId,
            medicamentoId,
        )

        if (usuarios.isEmpty()) {
            return 0
        }

        // Um identificador novo para cada transição real.
        // Todos os usuários notificados pela mesma transição
        // recebem a mesma chave de evento.
        val chaveEvento =
            "disponibilidade:$ubsId:$medicamentoId:${UUID.randomUUID()}"

        val criadoEm = Instant.now(relogio)

        val novosAvisos = usuarios.map { usuario ->
            Aviso(
                usuario = usuario,
                tipo = TipoAviso.MEDICAMENTO_DISPONIVEL,
                titulo = "Medicamento disponível",
                texto = "O medicamento ${medicamento.nome} " +
                    "está disponível na unidade ${ubs.nome}.",
                lido = false,
                criadoEm = criadoEm,
                chaveEvento = chaveEvento,
            )
        }

        avisos.saveAll(novosAvisos)

        return novosAvisos.size
    }
}
