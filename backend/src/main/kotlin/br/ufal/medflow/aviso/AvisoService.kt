package br.ufal.medflow.aviso

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.remessa.EtapaRemessa
import br.ufal.medflow.remessa.RemessaEventoRepository
import br.ufal.medflow.remessa.RemessaRepository
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Gera os avisos de uma UBS para os medicamentos que o cidadão acompanha, sem login:
 * o que já tem estoque, o que está em remessa e se a unidade está com a sincronização atrasada.
 */
@Service
@Transactional(readOnly = true)
class AvisoService(
    private val ubsRepository: UbsRepository,
    private val itemEstoqueRepository: ItemEstoqueRepository,
    private val remessaRepository: RemessaRepository,
    private val remessaEventoRepository: RemessaEventoRepository,
    private val relogio: Clock,
) {

    /** Do mais recente para o mais antigo; aviso sem data (UBS que nunca sincronizou) vai para o fim. */
    fun listar(ubsId: Long, medicamentoIds: Set<Long>): List<AvisoResponse> {
        val ubs = ubsRepository.findByIdOrNull(ubsId) ?: throw RecursoNaoEncontradoException("UBS", ubsId)

        val avisos = buildList {
            sincronizacaoAtrasada(ubs)?.let(::add)
            if (medicamentoIds.isNotEmpty()) {
                addAll(disponiveis(ubs, medicamentoIds))
                addAll(remessas(ubs, medicamentoIds))
            }
        }

        return avisos.sortedWith(compareBy(nullsLast(reverseOrder<Instant>())) { it.ocorridoEm })
    }

    private fun sincronizacaoAtrasada(ubs: Ubs): AvisoResponse? {
        val ultima = ubs.ultimaSincronizacao
        if (ultima != null && Duration.between(ultima, relogio.instant()) <= LIMITE_SINCRONIZACAO) return null

        return AvisoResponse(
            id = "sincronizacao-${ubs.id}-${ultima?.epochSecond ?: "nunca"}",
            tipo = TipoAviso.SINCRONIZACAO_ATRASADA,
            ubsId = requireNotNull(ubs.id),
            ubsNome = ubs.nome,
            ocorridoEm = ultima,
        )
    }

    /** Mesma regra do restante da API: só conta como disponível com status DISPONIVEL e quantidade > 0. */
    private fun disponiveis(ubs: Ubs, medicamentoIds: Set<Long>): List<AvisoResponse> =
        itemEstoqueRepository.findByUbs(requireNotNull(ubs.id))
            .filter { it.medicamento.id in medicamentoIds && it.status == StatusEstoque.DISPONIVEL && it.quantidade > 0 }
            .map { item ->
                AvisoResponse(
                    // Sem a data no id: o aviso já lido não volta a cada sincronização do estoque.
                    id = "disponivel-${ubs.id}-${item.medicamento.id}",
                    tipo = TipoAviso.DISPONIVEL,
                    ubsId = requireNotNull(ubs.id),
                    ubsNome = ubs.nome,
                    medicamentoId = item.medicamento.id,
                    medicamentoNome = item.medicamento.nome,
                    medicamentoConcentracao = item.medicamento.concentracao,
                    quantidade = item.quantidade,
                    ocorridoEm = item.atualizadoEm,
                )
            }

    /** Remessas ainda não concluídas; a etapa atual é a mais avançada registrada (ou PEDIDO, sem eventos). */
    private fun remessas(ubs: Ubs, medicamentoIds: Set<Long>): List<AvisoResponse> {
        val emAndamento = remessaRepository.findEmAndamentoDaUbs(requireNotNull(ubs.id))
            .filter { it.medicamento.id in medicamentoIds }
        if (emAndamento.isEmpty()) return emptyList()

        val ultimoEvento = remessaEventoRepository.findDasRemessas(emAndamento.map { requireNotNull(it.id) })
            .groupBy { it.remessa.id }
            .mapValues { (_, eventos) -> eventos.maxBy { it.etapa.ordinal } }

        return emAndamento.map { remessa ->
            val evento = ultimoEvento[remessa.id]
            val etapa = evento?.etapa ?: EtapaRemessa.PEDIDO
            AvisoResponse(
                id = "remessa-${remessa.id}-$etapa",
                tipo = TipoAviso.REMESSA,
                ubsId = requireNotNull(ubs.id),
                ubsNome = ubs.nome,
                medicamentoId = remessa.medicamento.id,
                medicamentoNome = remessa.medicamento.nome,
                medicamentoConcentracao = remessa.medicamento.concentracao,
                quantidade = remessa.quantidade,
                etapa = etapa,
                previsaoChegada = remessa.previsaoChegada,
                ocorridoEm = evento?.ocorridoEm ?: remessa.criadoEm,
            )
        }
    }

    companion object {
        /** O mesmo limite do aviso de sincronização da tela da UBS no front. */
        val LIMITE_SINCRONIZACAO: Duration = Duration.ofHours(24)
    }
}
