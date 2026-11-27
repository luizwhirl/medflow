package br.ufal.medflow.gestao

import br.ufal.medflow.auth.CredenciaisInvalidasException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val UBS_ID = "Filtra por uma UBS. Gestor com vínculo só pode informar a própria; sem ele, vale a rede toda."

/** Painel da gestão (só o papel GESTOR). Todas as rotas respeitam o escopo do gestor (ver `/api/gestao/escopo`). */
@RestController
@RequestMapping("/api/gestao")
class PainelGestaoController(private val painel: PainelGestaoService) {

    @Operation(
        summary = "Indicadores do painel",
        description = "KPIs de estoque, remessas, demanda da lista de desejos, lotes a vencer e sincronização. " +
            "O mínimo de cada medicamento é o seu limite de reservado ou, sem ele, o global.",
    )
    @GetMapping("/indicadores")
    fun indicadores(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = UBS_ID) @RequestParam(required = false) @Positive ubsId: Long?,
    ): IndicadoresResponse = painel.indicadores(usuarioId(jwt), ubsId)

    @Operation(
        summary = "Itens que pedem atenção",
        description = "Itens sem saldo ou com saldo até o mínimo, com pacientes aguardando e a remessa prevista. " +
            "Em falta primeiro, depois abaixo do mínimo e, por fim, os com pedido enviado.",
    )
    @GetMapping("/itens-atencao")
    fun itensAtencao(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = UBS_ID) @RequestParam(required = false) @Positive ubsId: Long?,
        @Parameter(description = "Quantos itens trazer (1 a 200). Sem ele, 50.")
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) limite: Int,
    ): List<ItemAtencaoResponse> = painel.itensAtencao(usuarioId(jwt), ubsId, limite)

    @Operation(
        summary = "Sugestões de remanejamento",
        description = "Quem tem saldo até o mínimo recebe de quem tem mais que o dobro do mínimo, na mesma cidade, " +
            "pela UBS mais próxima. Em falta primeiro.",
    )
    @GetMapping("/remanejamento")
    fun remanejamento(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = UBS_ID) @RequestParam(required = false) @Positive ubsId: Long?,
        @Parameter(description = "Quantas sugestões trazer (1 a 100). Sem ele, 20.")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limite: Int,
    ): List<RemanejamentoResponse> = painel.remanejamento(usuarioId(jwt), ubsId, limite)

    @Operation(
        summary = "Demanda reprimida",
        description = "Pacientes da lista de desejos que esperam um medicamento sem estoque disponível na UBS, " +
            "e os medicamentos mais procurados no app nos últimos 7 dias (contagem de buscas).",
    )
    @GetMapping("/demanda-reprimida")
    fun demandaReprimida(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = UBS_ID) @RequestParam(required = false) @Positive ubsId: Long?,
        @Parameter(description = "Tamanho de cada lista (1 a 100). Sem ele, 20.")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limite: Int,
    ): DemandaReprimidaResponse = painel.demandaReprimida(usuarioId(jwt), ubsId, limite)

    @Operation(summary = "Lotes vencendo", description = "Lotes com saldo que vencem até `dias` dias (inclui os já vencidos), do que vence primeiro.")
    @GetMapping("/lotes-vencendo")
    fun lotesVencendo(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = UBS_ID) @RequestParam(required = false) @Positive ubsId: Long?,
        @Parameter(description = "Prazo em dias (1 a 365). Sem ele, 60.")
        @RequestParam(defaultValue = "$DIAS_VENCIMENTO_PADRAO") @Min(1) @Max(365) dias: Int,
        @Parameter(description = "Quantos lotes trazer (1 a 200). Sem ele, 50.")
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) limite: Int,
    ): List<LoteVencendoResponse> = painel.lotesVencendo(usuarioId(jwt), ubsId, dias, limite)

    @Operation(summary = "UBS com sincronização atrasada", description = "UBS que nunca sincronizaram ou cuja posição é de mais de `dias` dias de calendário atrás (fuso de Maceió). Com 1, a posição de ontem ainda vale.")
    @GetMapping("/sincronizacao-atrasada")
    fun sincronizacaoAtrasada(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = UBS_ID) @RequestParam(required = false) @Positive ubsId: Long?,
        @Parameter(description = "Dias de calendário de tolerância (1 a 30). Sem ele, 1: a posição de ontem ainda vale.")
        @RequestParam(defaultValue = "$DIAS_ATRASO_PADRAO") @Min(1) @Max(30) dias: Int,
    ): List<SincronizacaoAtrasadaResponse> = painel.sincronizacaoAtrasada(usuarioId(jwt), ubsId, dias)

    private fun usuarioId(jwt: Jwt): Long = jwt.subject?.toLongOrNull() ?: throw CredenciaisInvalidasException()
}
