package br.ufal.medflow.aviso

import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Identifica UBS com dados atrasados e gera avisos aos dispositivos interessados. */
@Service
class AvisoAtrasoService(
    private val ubsRepository: UbsRepository,
    private val avisoRepository: AvisoRepository,
    private val relogio: Clock,

    @param:Value("\${app.avisos.atraso-dias:1}")
    private val atrasoDias: Long,
) {

    init {
        require(atrasoDias > 0) {
            "O prazo de atraso em dias deve ser maior que zero"
        }
    }

    /**
     * Verifica as UBS de um município e gera avisos sobre dados atrasados.
     *
     * Considera atrasadas as UBS sem referência ou com posição anterior
     * ao início do dia de ontem, no fuso de Maceió (prazo padrão de 1 dia).
     * Assim, uma posição de ontem permanece válida durante todo o dia de hoje.
     *
     * O resultado é a quantidade de avisos efetivamente inseridos.
     */
    @Transactional
    fun verificarMunicipio(codigoIbge: String): Int {
        val agora = Instant.now(relogio)
        val hojeLocal = agora.atZone(ZONA_MACEIO).toLocalDate()
        val limite = hojeLocal.minusDays(atrasoDias)
            .atStartOfDay(ZONA_MACEIO)
            .toInstant()

        val unidadesAtrasadas = ubsRepository.buscarComDadosAtrasados(
            codigoIbge,
            limite,
        )

        return unidadesAtrasadas.sumOf { ubs ->
            registrarAtraso(ubs, agora)
        }
    }

    /**
     * Insere um aviso por dispositivo interessado na UBS.
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

    private companion object {
        val ZONA_MACEIO: ZoneId = ZoneId.of("America/Maceio")
    }
}
