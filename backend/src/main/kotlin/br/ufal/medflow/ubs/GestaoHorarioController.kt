package br.ufal.medflow.ubs

import br.ufal.medflow.auth.CredenciaisInvalidasException
import br.ufal.medflow.common.ParametroInvalidoException
import br.ufal.medflow.gestao.EscopoGestaoService
import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalTime
import java.time.OffsetDateTime

/** Um turno no pedido de cadastro. `fecha` antes de `abre` vira a noite; igual a `abre`, dura 24 h. */
data class TurnoRequest(
    @field:NotNull val diaSemana: DiaSemana?,
    @JsonFormat(pattern = "HH:mm") @field:NotNull val abre: LocalTime?,
    @JsonFormat(pattern = "HH:mm") @field:NotNull val fecha: LocalTime?,
)

/** Todos os turnos da UBS, que substituem os anteriores. Lista vazia apaga o horário (situação desconhecida). */
data class AtualizarHorariosRequest(
    @field:NotNull @field:Size(max = MAX_TURNOS) @field:Valid val turnos: List<TurnoRequest>?,
)

/** Horário da UBS depois do cadastro, com a situação calculada agora (mesmos campos de `GET /api/ubs/{id}`). */
data class HorariosUbsResponse(
    val ubsId: Long,
    val horarios: List<HorarioResponse>,
    val abertaAgora: Boolean?,
    @get:JsonFormat(pattern = "HH:mm") val fechaAs: LocalTime?,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "America/Maceio")
    val fechaEm: OffsetDateTime?,
)

/** Até quatro turnos por dia, nos sete dias. */
const val MAX_TURNOS = 28

/**
 * Cadastro do horário de funcionamento pela gestão. É assim que as UBS reais ganham horário em produção:
 * a BNAFAR não informa horário, e o CNES só traz turnos genéricos ("manhã e tarde"), sem a hora de fechar.
 * Fica sob `/api/gestao`, então exige o papel GESTOR (`SecurityConfig`), e segue o escopo do gestor
 * (`EscopoGestaoService`): com vínculo, ele só cadastra o horário da própria UBS.
 */
@RestController
@RequestMapping("/api/gestao/ubs")
class GestaoHorarioController(
    private val escopoGestaoService: EscopoGestaoService,
    private val horarioFuncionamentoService: HorarioFuncionamentoService,
) {

    @Operation(
        summary = "Cadastra o horário de funcionamento da UBS",
        description = "Substitui todos os turnos da UBS pelos enviados (lista vazia apaga o horário). Só GESTOR.",
    )
    @PutMapping("/{id}/horarios")
    fun atualizarHorarios(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        @Valid @RequestBody pedido: AtualizarHorariosRequest,
    ): HorariosUbsResponse {
        // Escopo do gestor: com vínculo, só a própria UBS (outra responde 403, sem revelar se existe);
        // sem vínculo, qualquer UBS que exista (inexistente responde 404)
        escopoGestaoService.resolver(jwt.subject?.toLongOrNull() ?: throw CredenciaisInvalidasException(), id)

        // o @NotNull já barrou os nulos com 400; aqui só se converte para tipos não nulos
        val turnos = requireNotNull(pedido.turnos).map { Turno(it.diaSemana!!, it.abre!!, it.fecha!!) }
        if (turnos.groupBy { it.diaSemana to it.abre }.values.any { it.size > 1 }) {
            throw ParametroInvalidoException("turnos", "Há mais de um turno no mesmo dia começando no mesmo horário")
        }

        val funcionamento = horarioFuncionamentoService.substituir(id, turnos)
        return HorariosUbsResponse(
            ubsId = id,
            horarios = funcionamento?.horarios.orEmpty(),
            abertaAgora = funcionamento?.abertaAgora,
            fechaAs = funcionamento?.fechaAs,
            fechaEm = funcionamento?.fechaEm,
        )
    }
}
