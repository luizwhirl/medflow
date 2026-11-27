package br.ufal.medflow.ubs

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime

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
    /** `true` quando o pino é o centro do bairro: a coordenada da fonte estava errada e a unidade não foi achada no mapa. */
    val localizacaoAproximada: Boolean = false,
    val ultimaSincronizacao: Instant?,
    /** Turnos por dia da semana; vazio quando a UBS não tem horário cadastrado. */
    val horarios: List<HorarioResponse> = emptyList(),
    /** `null` quando a UBS não tem horário cadastrado. */
    val abertaAgora: Boolean? = null,
    /** Hora em que fecha (`HH:mm`), só quando está aberta e tem hora de fechar. */
    @get:JsonFormat(pattern = "HH:mm") val fechaAs: LocalTime? = null,
    /** Data e hora em que fecha, com fuso: num turno que vira a noite, é no dia seguinte. */
    @get:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "America/Maceio")
    val fechaEm: OffsetDateTime? = null,
) {
    fun com(funcionamento: FuncionamentoUbs?) = funcionamento?.let {
        copy(horarios = it.horarios, abertaAgora = it.abertaAgora, fechaAs = it.fechaAs, fechaEm = it.fechaEm)
    } ?: this
}

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
    localizacaoAproximada = localizacaoOrigem.aproximada,
    ultimaSincronizacao = ultimaSincronizacao,
)
