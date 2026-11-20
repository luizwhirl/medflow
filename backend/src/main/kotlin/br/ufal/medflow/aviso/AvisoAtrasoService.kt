package br.ufal.medflow.aviso

import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Identifica UBS com dados atrasados e gera avisos aos cidadãos interessados. */
@Service
class AvisoAtrasoService(
    private val ubsRepository: UbsRepository,
    private val avisoRepository: AvisoRepository,
    private val relogio: Clock,

    @param:Value("\${app.avisos.atraso-horas:24}")
    private val atrasoHoras: Long,
) {

    init {
        require(atrasoHoras > 0) {
            "O prazo de atraso dos avisos deve ser maior que zero"
        }
    }

    /**
     * Verifica as UBS de um município e gera avisos sobre dados atrasados.
     *
     * A consulta ao banco considera as unidades cuja última data
     * de referência é anterior ao limite permitido ou está ausente.
     *
     * O resultado é a quantidade de avisos efetivamente inseridos.
     */
    @Transactional
    fun verificarMunicipio(codigoIbge: String): Int {
        val agora = Instant.now(relogio)
        val limite = agora.minus(Duration.ofHours(atrasoHoras))

        val unidadesAtrasadas = ubsRepository.buscarComDadosAtrasados(
            codigoIbge,
            limite,
        )

        return unidadesAtrasadas.sumOf { ubs ->
            registrarAtraso(ubs, agora)
        }
    }

    /**
     * Insere um aviso por cidadão interessado na UBS.
     *
     * A chave é estável enquanto a data de referência
     * da unidade não mudar. A constraint de unicidade
     * impede avisos duplicados em verificações repetidas.
     */
    private fun registrarAtraso(
        ubs: Ubs,
        criadoEm: Instant,
    ): Int {
        val ubsId = requireNotNull(ubs.id) {
            "A UBS precisa estar persistida"
        }

        val titulo = "Dados da UBS desatualizados"

        val texto =
            "Os dados de estoque da unidade ${ubs.nome} estão " +
                "desatualizados. Consulte a data da última atualização " +
                "antes de se dirigir à unidade."

        val chaveEvento = construirChaveEvento(ubs)

        return avisoRepository.inserirAvisosDeAtraso(
            ubsId = ubsId,
            titulo = titulo,
            texto = texto,
            criadoEm = criadoEm,
            chaveEvento = chaveEvento,
        )
    }

    /**
     * Identifica o período de dados atrasados pela última
     * referência conhecida da UBS.
     *
     * Se a unidade não possui data, usa uma chave fixa.
     * Se ela receber dados novos e voltar a ficar atrasada,
     * poderá gerar outro aviso com uma chave diferente.
     */
    private fun construirChaveEvento(ubs: Ubs): String {
        val ubsId = requireNotNull(ubs.id)

        val referencia = ubs.ultimaSincronizacao
            ?.toEpochMilli()
            ?.toString()
            ?: "sem-data"

        return "atraso:$ubsId:$referencia"
    }
}
