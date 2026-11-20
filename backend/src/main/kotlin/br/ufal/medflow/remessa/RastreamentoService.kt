package br.ufal.medflow.remessa

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RastreamentoService(
    private val remessaRepository: RemessaRepository,
    private val eventoRepository: RemessaEventoRepository,
    private val medicamentoRepository: MedicamentoRepository,
    private val ubsRepository: UbsRepository,
) {

    /**
     * Remessa do medicamento na UBS. Se houver mais de uma, vale a que ainda está em andamento e chega primeiro;
     * sem nenhuma em andamento, a mais recente.
     */
    fun rastrear(medicamentoId: Long, ubsId: Long): RastreamentoResponse {
        if (!medicamentoRepository.existsById(medicamentoId)) throw RecursoNaoEncontradoException("Medicamento", medicamentoId)
        if (!ubsRepository.existsById(ubsId)) throw RecursoNaoEncontradoException("UBS", ubsId)

        val remessas = remessaRepository.findDaUbsEMedicamento(ubsId, medicamentoId)
        if (remessas.isEmpty()) return RastreamentoResponse(medicamentoId, ubsId, remessa = null)

        val eventosPorRemessa = eventoRepository.findDasRemessas(remessas.map { requireNotNull(it.id) })
            .groupBy { requireNotNull(it.remessa.id) }
        val emAndamento = remessas.filter { r -> eventosPorRemessa[r.id].orEmpty().none { it.etapa == EtapaRemessa.DISPONIVEL } }
        val escolhida = emAndamento.minWithOrNull(compareBy({ it.previsaoChegada }, { it.id })) ?: remessas.first()

        return RastreamentoResponse(medicamentoId, ubsId, escolhida.paraResposta(eventosPorRemessa[escolhida.id].orEmpty()))
    }

    private fun Remessa.paraResposta(eventos: List<RemessaEvento>): RemessaRastreamentoResponse {
        val quando = eventos.associate { it.etapa to it.ocorridoEm }
        val etapaAtual = EtapaRemessa.entries.lastOrNull { it in quando } ?: EtapaRemessa.PEDIDO
        return RemessaRastreamentoResponse(
            id = requireNotNull(id),
            quantidade = quantidade,
            previsaoChegada = previsaoChegada,
            etapaAtual = etapaAtual,
            concluida = EtapaRemessa.DISPONIVEL in quando,
            linhaDoTempo = EtapaRemessa.entries.map { EtapaRastreamentoResponse(it, quando[it]) },
        )
    }
}
