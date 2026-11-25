package br.ufal.medflow.busca

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.FUSO_UBS
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/**
 * Registro das buscas para o "mais procurados" do painel (PDS-49). Guarda só a contagem por dia, medicamento e UBS
 * de referência: nada do que foi digitado ou falado, nem o aparelho ou o horário. Os dias com mais de
 * `app.buscas.retencao-meses` meses são apagados por [LimpezaContagemBusca].
 */
@Service
class ContagemBuscaService(
    private val repositorio: ContagemBuscaRepository,
    private val medicamentoRepository: MedicamentoRepository,
    private val ubsRepository: UbsRepository,
    private val relogio: Clock,
    @param:Value("\${app.buscas.retencao-meses:12}")
    private val retencaoMeses: Long,
) {

    init {
        require(retencaoMeses > 0) { "A retenção das buscas deve ser de pelo menos um mês" }
    }

    @Transactional
    fun registrar(medicamentoId: Long, ubsId: Long?) {
        if (!medicamentoRepository.existsById(medicamentoId)) throw RecursoNaoEncontradoException("Medicamento", medicamentoId)
        if (ubsId != null && !ubsRepository.existsById(ubsId)) throw RecursoNaoEncontradoException("UBS", ubsId)
        repositorio.somar(hoje(), medicamentoId, ubsId)
    }

    /** Apaga as contagens mais antigas que a retenção e devolve quantas linhas saíram. */
    @Transactional
    fun apagarAntigas(): Int = repositorio.apagarAntesDe(hoje().minusMonths(retencaoMeses))

    private fun hoje(): LocalDate = LocalDate.now(relogio.withZone(FUSO_UBS))
}
