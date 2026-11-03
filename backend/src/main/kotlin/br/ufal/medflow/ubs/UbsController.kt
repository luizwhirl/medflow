package br.ufal.medflow.ubs

import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.estoque.ResumoEstoqueUbsResponse
import br.ufal.medflow.estoque.StatusEstoque
import org.springframework.data.repository.findByIdOrNull
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
) {

    @GetMapping
    fun listar(): List<UbsResponse> = ubsRepository.findAllByOrderByNomeAsc().map { it.toResponse() }

    @GetMapping("/{id}")
    fun buscar(@PathVariable id: Long): UbsResponse =
        (ubsRepository.findByIdOrNull(id) ?: throw RecursoNaoEncontradoException("UBS", id)).toResponse()

    @GetMapping("/{id}/estoque")
    fun estoque(
        @PathVariable id: Long,
        @RequestParam(required = false) status: StatusEstoque?,
    ): ResumoEstoqueUbsResponse =
        ubsEstoqueService.obterResumo(id, status)
}
