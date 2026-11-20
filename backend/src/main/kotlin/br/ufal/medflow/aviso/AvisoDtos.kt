
package br.ufal.medflow.aviso

import java.time.Instant

/** Dados de um aviso retornados ao cidadão autenticado. */
data class AvisoResponse(
    val id: Long,
    val tipo: TipoAviso,
    val titulo: String,
    val texto: String,
    val lido: Boolean,
    val criadoEm: Instant,
)

/** Resultado da operação de marcar todos os avisos como lidos. */
data class AvisosLidosResponse(
    val atualizados: Int,
)

/** Converte uma entidade persistida em resposta da API. */
fun Aviso.toResponse(): AvisoResponse =
    AvisoResponse(
        id = requireNotNull(id) {
            "Não é possível retornar um aviso ainda não persistido"
        },
        tipo = tipo,
        titulo = titulo,
        texto = texto,
        lido = lido,
        criadoEm = criadoEm,
    )
