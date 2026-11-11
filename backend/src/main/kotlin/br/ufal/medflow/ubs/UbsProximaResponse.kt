package br.ufal.medflow.ubs

import br.ufal.medflow.estoque.StatusEstoque
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * UBS da listagem (GET /api/ubs). Os três últimos campos só aparecem quando fazem sentido:
 * `distanciaKm` com lat/lng, e `status`/`quantidade` com medicamentoId (e só nas unidades que têm o item).
 */
data class UbsProximaResponse(
    val id: Long,
    val cnes: String,
    val nome: String,
    val tipo: TipoUnidade,
    val bairro: String,
    val endereco: String,
    val telefone: String?,
    val horario: String?,
    val latitude: Double,
    val longitude: Double,
    val ultimaSincronizacao: Instant?,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val distanciaKm: Double? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val status: StatusEstoque? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val quantidade: Int? = null,
)

fun Ubs.toProximaResponse(distanciaKm: Double? = null, status: StatusEstoque? = null, quantidade: Int? = null) =
    UbsProximaResponse(
        id = requireNotNull(id),
        cnes = cnes,
        nome = nome,
        tipo = tipo,
        bairro = bairro,
        endereco = endereco,
        telefone = telefone,
        horario = horario,
        latitude = latitude,
        longitude = longitude,
        ultimaSincronizacao = ultimaSincronizacao,
        distanciaKm = distanciaKm,
        status = status,
        quantidade = quantidade,
    )
