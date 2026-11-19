package br.ufal.medflow.common

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

/** Formato único das respostas paginadas da API. `pagina` começa em 0. */
data class PaginaResponse<T>(
    val itens: List<T>,
    val pagina: Int,
    val tamanho: Int,
    val totalItens: Long,
    val totalPaginas: Int,
)

fun <E : Any, T> Page<E>.toPaginaResponse(converter: (E) -> T): PaginaResponse<T> =
    PaginaResponse(
        itens = content.map(converter),
        pagina = number,
        tamanho = size,
        totalItens = totalElements,
        totalPaginas = totalPages,
    )

object Paginacao {
    const val TAMANHO_PADRAO = 20
    const val TAMANHO_MAXIMO = 100

    /** Página ordenada pelas propriedades informadas, com `id` no fim para a ordem ser estável entre páginas. */
    fun de(pagina: Int, tamanho: Int, vararg ordem: String): PageRequest =
        PageRequest.of(pagina, tamanho, Sort.by(*ordem, "id"))
}
