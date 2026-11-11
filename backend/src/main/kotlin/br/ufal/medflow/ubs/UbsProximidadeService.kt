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
     * UBS ordenadas pela distância de ([lat], [lng]). [raioKm] limita a distância (sem ele, devolve todas).
     * Com [medicamentoId], cada UBS que tem o item vem com o status e a quantidade dele.
     *
     * @throws RecursoNaoEncontradoException se o medicamento não existir
     */
    fun buscar(lat: Double, lng: Double, raioKm: Double?, medicamentoId: Long?): List<UbsProximaResponse> {
        if (medicamentoId != null && !medicamentoRepository.existsById(medicamentoId)) {
            throw RecursoNaoEncontradoException("Medicamento", medicamentoId)
        }

        val latRad = Math.toRadians(lat)
        val linhas = ubsRepository.buscarProximas(
            latRad = latRad,
            lngRad = Math.toRadians(lng),
            cosLat = cos(latRad),
            raioKm = raioKm ?: SEM_LIMITE_KM,
            medicamentoId = medicamentoId ?: SEM_MEDICAMENTO, // nenhum item tem esse id: o status vem nulo
        )

        return linhas.map { linha ->
            (linha[0] as Ubs).toProximaResponse(
                distanciaKm = round((linha[1] as Double) * 100) / 100,
                status = linha[2] as StatusEstoque?,
                quantidade = linha[3] as Int?,
            )
        }
    }

    private companion object {
        /** Maior que metade da circunferência da Terra (20.015 km): equivale a não ter raio. */
        const val SEM_LIMITE_KM = 20_100.0
        const val SEM_MEDICAMENTO = -1L
    }
}
