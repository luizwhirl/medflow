package br.ufal.medflow.gestao

import br.ufal.medflow.estoque.EstoqueProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Prazos padrão do painel: lotes que vencem em 60 dias e dados de antes de ontem (1 dia de calendário). */
const val DIAS_VENCIMENTO_PADRAO = 60
const val DIAS_ATRASO_PADRAO = 1

/** Fuso do "hoje" do painel (vencimento dos lotes e atraso da sincronização): o de Maceió, como no resto do projeto. */
internal val FUSO_PAINEL: ZoneId = ZoneId.of("America/Maceio")

@Service
@Transactional(readOnly = true)
class PainelGestaoService(
    private val escopoService: EscopoGestaoService,
    private val repositorio: PainelGestaoRepository,
    private val estoque: EstoqueProperties,
    private val relogio: Clock,
) {

    fun indicadores(usuarioId: Long, ubsIdPedida: Long?): IndicadoresResponse {
        val escopo = escopoService.descrever(usuarioId, ubsIdPedida)
        val ubsId = escopo.ubs?.id
        val itens = repositorio.contarItens(ubsId, estoque.limiteReservado)
        val remessas = repositorio.contarRemessas(ubsId, hoje())
        val demanda = repositorio.contarDemanda(ubsId)
        return IndicadoresResponse(
            ubs = escopo.ubs,
            restrito = escopo.restrito,
            ultimaSincronizacao = repositorio.ultimaSincronizacao(ubsId),
            itensEmFalta = itens.emFalta,
            emFaltaComRemessa = itens.emFaltaComRemessa,
            abaixoDoMinimo = itens.abaixoDoMinimo,
            remessasACaminho = remessas.aCaminho,
            remessasAtrasadas = remessas.atrasadas,
            proximaRemessa = remessas.proxima,
            pacientesAguardando = demanda.pacientes,
            medicamentosAguardados = demanda.medicamentos,
            lotesVencendo = repositorio.contarLotesVencendo(ubsId, hoje().plusDays(DIAS_VENCIMENTO_PADRAO.toLong())),
            ubsComSincronizacaoAtrasada = repositorio.contarSincronizacaoAtrasada(ubsId, limiteDeAtraso(DIAS_ATRASO_PADRAO)),
        )
    }

    fun itensAtencao(usuarioId: Long, ubsIdPedida: Long?, limite: Int): List<ItemAtencaoResponse> {
        val ubsId = escopoService.resolver(usuarioId, ubsIdPedida).ubsId
        return repositorio.itensAtencao(ubsId, estoque.limiteReservado, limite)
    }

    fun remanejamento(usuarioId: Long, ubsIdPedida: Long?, limite: Int): List<RemanejamentoResponse> {
        val ubsId = escopoService.resolver(usuarioId, ubsIdPedida).ubsId
        return repositorio.sugerirRemanejamento(ubsId, estoque.limiteReservado, limite)
    }

    fun demandaReprimida(usuarioId: Long, ubsIdPedida: Long?, limite: Int): DemandaReprimidaResponse {
        val ubsId = escopoService.resolver(usuarioId, ubsIdPedida).ubsId
        return DemandaReprimidaResponse(
            itens = repositorio.demandaPorUbs(ubsId, limite),
            maisProcurados = repositorio.maisProcurados(ubsId, limite),
        )
    }

    fun lotesVencendo(usuarioId: Long, ubsIdPedida: Long?, dias: Int, limite: Int): List<LoteVencendoResponse> {
        val ubsId = escopoService.resolver(usuarioId, ubsIdPedida).ubsId
        val hoje = hoje()
        return repositorio.lotesVencendo(ubsId, hoje.plusDays(dias.toLong()), hoje, limite)
    }

    fun sincronizacaoAtrasada(usuarioId: Long, ubsIdPedida: Long?, dias: Int): List<SincronizacaoAtrasadaResponse> {
        val ubsId = escopoService.resolver(usuarioId, ubsIdPedida).ubsId
        return repositorio.ubsComSincronizacaoAtrasada(ubsId, limiteDeAtraso(dias), hoje())
    }

    private fun hoje(): LocalDate = LocalDate.now(relogio.withZone(FUSO_PAINEL))

    /**
     * A `ultimaSincronizacao` das UBS que vêm da BNAFAR é a data da posição gravada à meia-noite, e não a hora em que
     * sincronizou. Por isso o atraso é em dias de calendário: com `dias = 1`, a posição de ontem continua valendo o
     * dia todo de hoje e só a de anteontem (ou mais antiga) é atrasada. O limite é o início do dia `hoje - dias`.
     */
    private fun limiteDeAtraso(dias: Int): Instant = hoje().minusDays(dias.toLong()).atStartOfDay(FUSO_PAINEL).toInstant()
}
