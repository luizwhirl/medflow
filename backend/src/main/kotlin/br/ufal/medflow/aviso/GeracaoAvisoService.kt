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
     * Não gera um novo aviso se o cidadão já possui um aviso
     * não lido para o mesmo medicamento e UBS.
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

        val prefixoEvento = "disponibilidade:$ubsId:$medicamentoId:"

        val usuarioIds = usuarios.map { usuario ->
            requireNotNull(usuario.id) {
                "O usuário interessado precisa estar persistido"
            }
        }

        // Consulta o banco uma única vez para identificar os cidadãos
        // que já possuem um aviso pendente dessa UBS e medicamento.
        val usuariosComAvisoPendente =
            avisos.buscarUsuariosComDisponibilidadeNaoLida(
                usuarioIds,
                TipoAviso.MEDICAMENTO_DISPONIVEL,
                prefixoEvento,
            ).toSet()

        val usuariosParaNotificar = usuarios.filter { usuario ->
            usuario.id !in usuariosComAvisoPendente
        }

        if (usuariosParaNotificar.isEmpty()) {
            return 0
        }

        // Identificador novo para esta transição.
        // Todos os cidadãos notificados recebem a mesma chave.
        val chaveEvento = "$prefixoEvento${UUID.randomUUID()}"
        val criadoEm = Instant.now(relogio)

        val novosAvisos = usuariosParaNotificar.map { usuario ->
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
