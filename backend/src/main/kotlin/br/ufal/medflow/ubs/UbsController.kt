package br.ufal.medflow.ubs

import br.ufal.medflow.common.PaginaResponse
import br.ufal.medflow.common.Paginacao
import br.ufal.medflow.common.ParametroInvalidoException
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ItemEstoqueResponse
import br.ufal.medflow.estoque.ResumoEstoqueUbsResponse
import br.ufal.medflow.estoque.StatusEstoque
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ubs")
@Transactional(readOnly = true)
class UbsController(
    private val ubsRepository: UbsRepository,
    private val ubsEstoqueService: UbsEstoqueService,
    private val ubsProximidadeService: UbsProximidadeService,
    private val horarioFuncionamentoService: HorarioFuncionamentoService,
) {

    @Operation(
        summary = "Lista as UBS",
        description = "Sem parâmetros, em ordem de nome. Com lat e lng, da mais próxima para a mais distante, " +
            "com a distância em km, até raioKm (padrão 20 km). Com medicamentoId, cada UBS que tem o item traz o " +
            "status e a quantidade. Cada UBS traz os horários, abertaAgora e fechaAs (fuso de Maceió); com " +
            "abertas=true, só as abertas agora.",
    )
    @GetMapping
    fun listar(
        @Parameter(description = "Latitude do ponto de referência (-90 a 90). Vai sempre junto com lng.")
        @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") lat: Double?,
        @Parameter(description = "Longitude do ponto de referência (-180 a 180). Vai sempre junto com lat.")
        @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") lng: Double?,
        @Parameter(description = "Distância máxima em km, maior que zero. Exige lat e lng. Sem ele, usa 20 km.")
        @RequestParam(required = false) @DecimalMin(value = "0.0", inclusive = false) raioKm: Double?,
        @Parameter(description = "Traz o status e a quantidade desse medicamento em cada UBS que o tem. Exige lat e lng.")
        @RequestParam(required = false) medicamentoId: Long?,
        @Parameter(description = "Com true, só as UBS abertas agora (fuso de Maceió). UBS sem horário cadastrado ficam de fora.")
        @RequestParam(required = false) abertas: Boolean?,
    ): List<UbsProximaResponse> {
        val unidades = if (lat == null && lng == null) {
            if (raioKm != null || medicamentoId != null) {
                throw ParametroInvalidoException("lat/lng", "raioKm e medicamentoId exigem lat e lng")
            }
            ubsRepository.findVisiveis().map { it.toProximaResponse() }
        } else {
            if (lat == null || lng == null) throw ParametroInvalidoException("lat/lng", "Informe lat e lng juntos")
            ubsProximidadeService.buscar(lat, lng, raioKm, medicamentoId)
        }

        val funcionamento = horarioFuncionamentoService.buscar(unidades.map { it.id })
        val comHorario = unidades.map { it.com(funcionamento[it.id]) }
        return if (abertas == true) comHorario.filter { it.abertaAgora == true } else comHorario
    }

    @Operation(
        summary = "Lista os bairros com UBS",
        description = "Bairros que têm ao menos uma unidade, sem repetição e em ordem alfabética. " +
            "Usado na boas-vindas para quem não quer compartilhar a localização.",
    )
    @GetMapping("/bairros")
    fun bairros(): List<String> = ubsRepository.findBairros()

    @GetMapping("/{id}")
    fun buscar(@PathVariable id: Long): UbsResponse {
        val ubs = (ubsRepository.findVisivel(id) ?: throw RecursoNaoEncontradoException("UBS", id)).toResponse()
        return ubs.com(horarioFuncionamentoService.buscar(listOf(id))[id])
    }

    @Operation(summary = "Estoque da UBS", description = "Em ordem de nome do medicamento, paginado.")
    @GetMapping("/{id}/estoque")
    fun estoque(
        @PathVariable id: Long,
        @RequestParam(required = false) status: StatusEstoque?,
        @Parameter(description = "Página, a partir de 0.")
        @RequestParam(defaultValue = "0") @Min(0) pagina: Int,
        @Parameter(description = "Itens por página, de 1 a ${Paginacao.TAMANHO_MAXIMO}.")
        @RequestParam(defaultValue = "${Paginacao.TAMANHO_PADRAO}") @Min(1) @Max(Paginacao.TAMANHO_MAXIMO.toLong())
        tamanho: Int,
    ): PaginaResponse<ItemEstoqueResponse> =
        ubsEstoqueService.listarItens(id, status, Paginacao.de(pagina, tamanho, "medicamento.nome"))

    @GetMapping("/{id}/estoque/resumo")
    fun resumoEstoque(
        @PathVariable id: Long,
        @RequestParam(required = false) status: StatusEstoque?,
    ): ResumoEstoqueUbsResponse =
        ubsEstoqueService.obterResumo(id, status)
}
