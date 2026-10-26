package br.ufal.medflow.ubs

import java.time.Instant

data class UbsResponse(
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
)

fun Ubs.toResponse() = UbsResponse(
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
)
