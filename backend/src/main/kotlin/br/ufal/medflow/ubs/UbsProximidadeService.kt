package br.ufal.medflow.ubs

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.medicamento.MedicamentoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.cos
import kotlin.math.round

@Service
@Transactional(readOnly = true)
class UbsProximidadeService(
    private val ubsRepository: UbsRepository,
    private val medicamentoRepository: MedicamentoRepository,
) {

    /**
     * UBS ordenadas pela distância de ([lat], [lng]), até [raioKm] (sem ele, [RAIO_PADRAO_KM]).
     * Com [medicamentoId], cada UBS que tem o item vem com o status e a quantidade dele.
     *
     * @throws RecursoNaoEncontradoException se o medicamento não existir
     */
    fun buscar(lat: Double, lng: Double, raioKm: Double?, medicamentoId: Long?): List<UbsProximaResponse> {
        if (medicamentoId != null && !medicamentoRepository.existsById(medicamentoId)) {
            throw RecursoNaoEncontradoException("Medicamento", medicamentoId)
        }

        val raio = raioKm ?: RAIO_PADRAO_KM
        val caixa = CaixaGeografica.em(lat, lng, raio)
        val latRad = Math.toRadians(lat)
        val linhas = ubsRepository.buscarProximas(
            latRad = latRad,
            lngRad = Math.toRadians(lng),
            cosLat = cos(latRad),
            raioKm = raio,
            latMin = caixa.latMin,
            latMax = caixa.latMax,
            lngMin = caixa.lngMin,
            lngMax = caixa.lngMax,
            medicamentoId = medicamentoId,
        )

        return linhas.map { linha ->
            (linha[0] as Ubs).toProximaResponse(
                distanciaKm = round((linha[1] as Double) * 100) / 100,
                status = linha[2] as StatusEstoque?,
                quantidade = linha[3] as Int?,
            )
        }
    }

    companion object {
        /** Raio usado quando o cliente não informa `raioKm`, para não varrer todas as UBS do banco. */
        const val RAIO_PADRAO_KM = 20.0
    }
}
