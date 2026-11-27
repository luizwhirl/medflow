package br.ufal.medflow.gestao

import br.ufal.medflow.auth.CredenciaisInvalidasException
import br.ufal.medflow.common.ParametroInvalidoException
import br.ufal.medflow.integracao.LimitesMunicipais
import br.ufal.medflow.integracao.Ponto
import br.ufal.medflow.sincronizacao.SincronizacaoProperties
import br.ufal.medflow.ubs.OrigemLocalizacao
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class LocalizacaoUbsRequest(
    @field:NotNull @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val latitude: Double?,
    @field:NotNull @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val longitude: Double?,
)

/** `origem`: FONTE, LUGAR, BAIRRO (aproximada), MANUAL ou INVALIDA (a unidade não aparece para o cidadão). */
data class LocalizacaoUbsResponse(
    val ubsId: Long,
    val nome: String,
    val endereco: String,
    val bairro: String,
    val latitude: Double,
    val longitude: Double,
    val origem: OrigemLocalizacao,
    val aproximada: Boolean,
)

private fun Ubs.toLocalizacaoResponse() = LocalizacaoUbsResponse(
    ubsId = requireNotNull(id),
    nome = nome,
    endereco = endereco,
    bairro = bairro,
    latitude = latitude,
    longitude = longitude,
    origem = localizacaoOrigem,
    aproximada = localizacaoOrigem.aproximada,
)

@Service
class LocalizacaoUbsService(
    private val escopoService: EscopoGestaoService,
    private val ubsRepository: UbsRepository,
    private val limites: LimitesMunicipais,
    private val sincronizacao: SincronizacaoProperties,
) {

    @Transactional(readOnly = true)
    fun consultar(usuarioId: Long, ubsId: Long): LocalizacaoUbsResponse = ubsDoEscopo(usuarioId, ubsId).toLocalizacaoResponse()

    /**
     * Grava o pino que o gestor conferiu. Vale acima da fonte nas próximas sincronizações (origem MANUAL) e faz a UBS
     * voltar para a lista e o mapa do cidadão. Só aceita pontos dentro do município da UBS (com a mesma tolerância da
     * sincronização), para um clique errado não mandar a unidade para outra cidade.
     */
    @Transactional
    fun corrigir(usuarioId: Long, ubsId: Long, pedido: LocalizacaoUbsRequest): LocalizacaoUbsResponse {
        val ubs = ubsDoEscopo(usuarioId, ubsId)
        val ponto = Ponto(requireNotNull(pedido.latitude), requireNotNull(pedido.longitude))
        if (!limites.limite(ubs.codigoIbgeMunicipio).contem(ponto, sincronizacao.toleranciaLimiteKm)) {
            throw ParametroInvalidoException("latitude/longitude", "O ponto fica fora do município da UBS")
        }
        ubs.latitude = ponto.latitude
        ubs.longitude = ponto.longitude
        ubs.localizacaoOrigem = OrigemLocalizacao.MANUAL
        return ubs.toLocalizacaoResponse()
    }

    /** Gestor com vínculo só mexe na própria UBS (403 nas outras); sem vínculo, em qualquer uma. */
    private fun ubsDoEscopo(usuarioId: Long, ubsId: Long): Ubs = requireNotNull(escopoService.resolver(usuarioId, ubsId).ubs)
}

@RestController
@RequestMapping("/api/gestao/ubs/{ubsId}/localizacao")
class LocalizacaoUbsController(private val servico: LocalizacaoUbsService) {

    @Operation(summary = "Localização da UBS no mapa", description = "Coordenada atual e de onde ela veio (origem).")
    @GetMapping
    fun consultar(@AuthenticationPrincipal jwt: Jwt, @PathVariable @Positive ubsId: Long): LocalizacaoUbsResponse =
        servico.consultar(usuarioId(jwt), ubsId)

    @Operation(
        summary = "Corrige o pino da UBS",
        description = "Grava a coordenada conferida pelo gestor. Vale acima da BNAFAR nas próximas sincronizações. " +
            "O ponto precisa estar dentro do município da UBS (400 se não estiver).",
    )
    @PutMapping
    fun corrigir(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable @Positive ubsId: Long,
        @Valid @RequestBody pedido: LocalizacaoUbsRequest,
    ): LocalizacaoUbsResponse = servico.corrigir(usuarioId(jwt), ubsId, pedido)

    private fun usuarioId(jwt: Jwt): Long = jwt.subject?.toLongOrNull() ?: throw CredenciaisInvalidasException()
}
