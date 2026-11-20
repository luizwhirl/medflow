package br.ufal.medflow.perfil

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/**
 * Dados da UBS escolhida como referência pelo cidadão.
 *
 * A UBS é consultada pelo ID, sem duplicar suas informações
 * na tabela de usuários.
 */
data class UbsReferenciaResponse(
    val id: Long,
    val cnes: String,
    val nome: String,
    val bairro: String,
    val endereco: String,
)

/**
 * Perfil do cidadão autenticado.
 *
 * Retornado pelo GET /api/me e pelo PUT /api/me.
 */
data class PerfilCidadaoResponse(
    val id: Long,
    val nome: String,
    val email: String,
    val ubsReferencia: UbsReferenciaResponse?,
    val bairro: String?,
    val latitudeAproximada: Double?,
    val longitudeAproximada: Double?,
    val letraMaior: Boolean,
    val salvarBuscasRecentes: Boolean,
    val buscasRecentes: List<String>,
)

/**
 * Atualização completa do perfil do cidadão.
 *
 * O ID do usuário não é recebido no corpo: ele será
 * identificado pelo JWT da requisição.
 *
 * Campos opcionais podem ser enviados como null.
 */
data class AtualizarPerfilRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val nome: String,

    @field:Positive
    val ubsReferenciaId: Long?,

    @field:Size(max = 120)
    val bairro: String?,

    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val latitudeAproximada: Double?,

    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val longitudeAproximada: Double?,

    val letraMaior: Boolean,

    val salvarBuscasRecentes: Boolean,

    @field:Size(max = 10)
    val buscasRecentes: List<String>,
)
