package br.ufal.medflow.busca

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

/** Contagem diária de buscas por medicamento e UBS de referência (tabela `contagem_busca`, V17). */
@Repository
class ContagemBuscaRepository(jdbcTemplate: JdbcTemplate) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    /** Soma uma busca na linha do dia; a primeira do dia cria a linha. Atômico, mesmo com buscas simultâneas. */
    fun somar(dia: LocalDate, medicamentoId: Long, ubsId: Long?) {
        jdbc.update(
            """
            INSERT INTO contagem_busca (dia, medicamento_id, ubs_id, total)
            VALUES (:dia, :medicamentoId, :ubsId, 1)
            ON CONFLICT (dia, medicamento_id, COALESCE(ubs_id, 0))
            DO UPDATE SET total = contagem_busca.total + 1
            """,
            MapSqlParameterSource()
                .addValue("dia", dia)
                .addValue("medicamentoId", medicamentoId)
                .addValue("ubsId", ubsId),
        )
    }

    /** Apaga os dias anteriores a [limite] e devolve quantas linhas saíram. */
    fun apagarAntesDe(limite: LocalDate): Int =
        jdbc.update("DELETE FROM contagem_busca WHERE dia < :limite", MapSqlParameterSource("limite", limite))
}
