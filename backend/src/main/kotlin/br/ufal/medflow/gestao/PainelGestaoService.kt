package br.ufal.medflow.gestao

import br.ufal.medflow.estoque.EstoqueProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Prazos padrão do painel: lotes que vencem em 60 dias e dados com mais de 24 horas. */
const val DIAS_VENCIMENTO_PADRAO = 60
const val HORAS_ATRASO_PADRAO = 24

/** Dia de hoje para o vencimento dos lotes: o fuso de Maceió, como no resto do projeto. */
private val FUSO_PAINEL: ZoneId = ZoneId.of("America/Maceio")

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
        val remessas = repositorio.contarRemessas(ubsId)
        val demanda = repositorio.contarDemanda(ubsId)
        return IndicadoresResponse(
            ubs = escopo.ubs,
            restrito = escopo.restrito,
            ultimaSincronizacao = repositorio.ultimaSincronizacao(ubsId),
            itensEmFalta = itens.emFalta,
            emFaltaComRemessa = itens.emFaltaComRemessa,
            abaixoDoMinimo = itens.abaixoDoMinimo,
            remessasACaminho = remessas.aCaminho,
            proximaRemessa = remessas.proxima,
            pacientesAguardando = demanda.pacientes,
            medicamentosAguardados = demanda.medicamentos,
            lotesVencendo = repositorio.contarLotesVencendo(ubsId, hoje().plusDays(DIAS_VENCIMENTO_PADRAO.toLong())),
            ubsComSincronizacaoAtrasada = repositorio.contarSincronizacaoAtrasada(ubsId, limiteDeAtraso(HORAS_ATRASO_PADRAO)),
        )
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

    fun sincronizacaoAtrasada(usuarioId: Long, ubsIdPedida: Long?, horas: Int): List<SincronizacaoAtrasadaResponse> {
        val ubsId = escopoService.resolver(usuarioId, ubsIdPedida).ubsId
        return repositorio.ubsComSincronizacaoAtrasada(ubsId, limiteDeAtraso(horas), Instant.now(relogio))
    }

    private fun hoje(): LocalDate = LocalDate.now(relogio.withZone(FUSO_PAINEL))

    private fun limiteDeAtraso(horas: Int): Instant = Instant.now(relogio).minus(Duration.ofHours(horas.toLong()))
}
