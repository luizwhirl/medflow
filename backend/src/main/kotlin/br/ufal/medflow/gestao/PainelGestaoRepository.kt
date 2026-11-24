package br.ufal.medflow.gestao

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * Consultas agregadas do painel da gestão, em SQL. Todas aceitam `ubsId`: com ele, só aquela UBS; sem ele, a rede.
 * O mínimo de um item é `medicamento.limite_reservado` ou, se for nulo, o limite global.
 */
@Repository
class PainelGestaoRepository(jdbcTemplate: JdbcTemplate) {

    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    private fun escopo(coluna: String, ubsId: Long?) = if (ubsId == null) "TRUE" else "$coluna = :ubsId"

    private fun params(ubsId: Long?) = MapSqlParameterSource().addValue("ubsId", ubsId)

    private fun ResultSet.data(coluna: String): LocalDate? = getObject(coluna, LocalDate::class.java)

    private fun ResultSet.instante(coluna: String): Instant? = getObject(coluna, Timestamp::class.java)?.toInstant()

    fun contarItens(ubsId: Long?, limiteGlobal: Int): ContagemItens = jdbc.queryForObject(
        """
        SELECT
            COUNT(*) FILTER (WHERE i.quantidade <= 0) AS em_falta,
            COUNT(*) FILTER (WHERE i.quantidade <= 0 AND EXISTS (
                SELECT 1 FROM remessa r
                WHERE r.ubs_id = i.ubs_id AND r.medicamento_id = i.medicamento_id AND $REMESSA_EM_ANDAMENTO
            )) AS em_falta_com_remessa,
            COUNT(*) FILTER (WHERE i.quantidade > 0 AND i.quantidade <= COALESCE(m.limite_reservado, :limiteGlobal)) AS abaixo_do_minimo
        FROM item_estoque i
        JOIN medicamento m ON m.id = i.medicamento_id
        WHERE ${escopo("i.ubs_id", ubsId)}
        """,
        params(ubsId).addValue("limiteGlobal", limiteGlobal),
    ) { rs, _ -> ContagemItens(rs.getInt("em_falta"), rs.getInt("em_falta_com_remessa"), rs.getInt("abaixo_do_minimo")) }!!

    /**
     * "A caminho" é a remessa que ainda não foi recebida (sem as etapas RECEBIDO e DISPONIVEL). A "próxima" é a de
     * menor previsão que ainda não passou; as que já passaram da previsão contam à parte, como atrasadas.
     */
    fun contarRemessas(ubsId: Long?, hoje: LocalDate): ContagemRemessas = jdbc.queryForObject(
        """
        SELECT COUNT(*) AS total,
               COUNT(*) FILTER (WHERE r.previsao_chegada < :hoje) AS atrasadas,
               MIN(r.previsao_chegada) FILTER (WHERE r.previsao_chegada >= :hoje) AS proxima
        FROM remessa r
        WHERE $REMESSA_A_CAMINHO AND ${escopo("r.ubs_id", ubsId)}
        """,
        params(ubsId).addValue("hoje", hoje),
    ) { rs, _ -> ContagemRemessas(rs.getInt("total"), rs.getInt("atrasadas"), rs.data("proxima")) }!!

    /**
     * Pacientes (e medicamentos) da lista de desejos cujo item não está disponível na UBS desejada.
     * Sem login, cada paciente é um aparelho distinto (dispositivo_id).
     */
    fun contarDemanda(ubsId: Long?): ContagemDemanda = jdbc.queryForObject(
        """
        SELECT COUNT(DISTINCT w.dispositivo_id) AS pacientes, COUNT(DISTINCT w.medicamento_id) AS medicamentos
        FROM item_lista_desejo w
        LEFT JOIN item_estoque i ON i.ubs_id = w.ubs_id AND i.medicamento_id = w.medicamento_id
        WHERE $NAO_DISPONIVEL AND ${escopo("w.ubs_id", ubsId)}
        """,
        params(ubsId),
    ) { rs, _ -> ContagemDemanda(rs.getInt("pacientes"), rs.getInt("medicamentos")) }!!

    fun contarLotesVencendo(ubsId: Long?, ate: LocalDate): Int = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM lote_estoque l JOIN item_estoque i ON i.id = l.item_estoque_id
        WHERE l.quantidade > 0 AND l.validade <= :ate AND ${escopo("i.ubs_id", ubsId)}
        """,
        params(ubsId).addValue("ate", ate),
        Int::class.java,
    )!!

    fun contarSincronizacaoAtrasada(ubsId: Long?, limite: Instant): Int = jdbc.queryForObject(
        "SELECT COUNT(*) FROM ubs u WHERE (u.ultima_sincronizacao IS NULL OR u.ultima_sincronizacao < :limite) AND ${escopo("u.id", ubsId)}",
        params(ubsId).addValue("limite", Timestamp.from(limite)),
        Int::class.java,
    )!!

    fun ultimaSincronizacao(ubsId: Long?): Instant? = jdbc.queryForObject(
        "SELECT MAX(u.ultima_sincronizacao) AS ultima FROM ubs u WHERE ${escopo("u.id", ubsId)}",
        params(ubsId),
    ) { rs, _ -> rs.instante("ultima") }

    /**
     * Quem precisa (saldo até o mínimo e abaixo do dobro dele) recebe de quem tem sobra (saldo acima do dobro do
     * mínimo), na mesma cidade e pela UBS de origem mais próxima. O pedido é o menor entre a falta e a sobra.
     */
    fun sugerirRemanejamento(ubsId: Long?, limiteGlobal: Int, limite: Int): List<RemanejamentoResponse> = jdbc.query(
        """
        SELECT * FROM (
            SELECT DISTINCT ON (n.id)
                m.id AS medicamento_id, m.nome AS medicamento_nome, m.concentracao,
                n.ubs_id AS destino_id, ud.nome AS destino_nome, n.quantidade AS quantidade_destino, n.minimo,
                s.ubs_id AS origem_id, uo.nome AS origem_nome, s.quantidade AS quantidade_origem,
                2 * 6371 * ASIN(SQRT(
                    POWER(SIN(RADIANS(uo.latitude - ud.latitude) / 2), 2)
                    + COS(RADIANS(ud.latitude)) * COS(RADIANS(uo.latitude)) * POWER(SIN(RADIANS(uo.longitude - ud.longitude) / 2), 2)
                )) AS distancia_km,
                LEAST(2 * n.minimo - n.quantidade, s.quantidade - 2 * s.minimo) AS sugerida
            FROM (
                SELECT i.id, i.ubs_id, i.medicamento_id, i.quantidade, COALESCE(m.limite_reservado, :limiteGlobal) AS minimo
                FROM item_estoque i JOIN medicamento m ON m.id = i.medicamento_id
                WHERE ${escopo("i.ubs_id", ubsId)}
                  AND i.quantidade <= COALESCE(m.limite_reservado, :limiteGlobal)
                  AND 2 * COALESCE(m.limite_reservado, :limiteGlobal) - i.quantidade > 0
            ) n
            JOIN (
                SELECT i.ubs_id, i.medicamento_id, i.quantidade, COALESCE(m.limite_reservado, :limiteGlobal) AS minimo
                FROM item_estoque i JOIN medicamento m ON m.id = i.medicamento_id
                WHERE i.quantidade > 2 * COALESCE(m.limite_reservado, :limiteGlobal)
            ) s ON s.medicamento_id = n.medicamento_id AND s.ubs_id <> n.ubs_id
            JOIN ubs ud ON ud.id = n.ubs_id
            JOIN ubs uo ON uo.id = s.ubs_id AND uo.codigo_ibge_municipio = ud.codigo_ibge_municipio
            JOIN medicamento m ON m.id = n.medicamento_id
            ORDER BY n.id, distancia_km, s.ubs_id
        ) sugestoes
        ORDER BY (quantidade_destino = 0) DESC, sugerida DESC, medicamento_nome, destino_nome
        LIMIT :limite
        """,
        params(ubsId).addValue("limiteGlobal", limiteGlobal).addValue("limite", limite),
    ) { rs, _ ->
        RemanejamentoResponse(
            medicamento = MedicamentoResumoResponse(rs.getLong("medicamento_id"), nomeComConcentracao(rs)),
            destino = UbsResumoResponse(rs.getLong("destino_id"), rs.getString("destino_nome")),
            quantidadeDestino = rs.getInt("quantidade_destino"),
            minimo = rs.getInt("minimo"),
            origem = UbsResumoResponse(rs.getLong("origem_id"), rs.getString("origem_nome")),
            quantidadeOrigem = rs.getInt("quantidade_origem"),
            distanciaKm = Math.round(rs.getDouble("distancia_km") * 100) / 100.0,
            quantidadeSugerida = rs.getInt("sugerida"),
        )
    }

    fun demandaPorUbs(ubsId: Long?, limite: Int): List<DemandaItemResponse> = jdbc.query(
        """
        SELECT w.ubs_id, u.nome AS ubs_nome, w.medicamento_id, m.nome AS medicamento_nome, m.concentracao,
               COUNT(DISTINCT w.dispositivo_id) AS pacientes, MAX(i.status) AS status, MAX(i.quantidade) AS quantidade
        FROM item_lista_desejo w
        JOIN ubs u ON u.id = w.ubs_id
        JOIN medicamento m ON m.id = w.medicamento_id
        LEFT JOIN item_estoque i ON i.ubs_id = w.ubs_id AND i.medicamento_id = w.medicamento_id
        WHERE $NAO_DISPONIVEL AND ${escopo("w.ubs_id", ubsId)}
        GROUP BY w.ubs_id, u.nome, w.medicamento_id, m.nome, m.concentracao
        ORDER BY pacientes DESC, m.nome, u.nome
        LIMIT :limite
        """,
        params(ubsId).addValue("limite", limite),
    ) { rs, _ ->
        DemandaItemResponse(
            ubs = UbsResumoResponse(rs.getLong("ubs_id"), rs.getString("ubs_nome")),
            medicamento = MedicamentoResumoResponse(rs.getLong("medicamento_id"), nomeComConcentracao(rs)),
            pacientesAguardando = rs.getInt("pacientes"),
            status = rs.getString("status"),
            quantidade = rs.getObject("quantidade") as Int?,
        )
    }

    /** Medicamentos mais desejados na lista de desejos (qualquer situação do estoque), no escopo. */
    fun maisProcurados(ubsId: Long?, limite: Int): List<MaisProcuradoResponse> = jdbc.query(
        """
        SELECT w.medicamento_id, m.nome AS medicamento_nome, m.concentracao, COUNT(DISTINCT w.dispositivo_id) AS pacientes
        FROM item_lista_desejo w JOIN medicamento m ON m.id = w.medicamento_id
        WHERE ${escopo("w.ubs_id", ubsId)}
        GROUP BY w.medicamento_id, m.nome, m.concentracao
        ORDER BY pacientes DESC, m.nome
        LIMIT :limite
        """,
        params(ubsId).addValue("limite", limite),
    ) { rs, _ ->
        MaisProcuradoResponse(MedicamentoResumoResponse(rs.getLong("medicamento_id"), nomeComConcentracao(rs)), rs.getInt("pacientes"))
    }

    fun lotesVencendo(ubsId: Long?, ate: LocalDate, hoje: LocalDate, limite: Int): List<LoteVencendoResponse> = jdbc.query(
        """
        SELECT l.id, l.numero_lote, l.validade, l.quantidade, i.ubs_id, u.nome AS ubs_nome,
               i.medicamento_id, m.nome AS medicamento_nome, m.concentracao
        FROM lote_estoque l
        JOIN item_estoque i ON i.id = l.item_estoque_id
        JOIN ubs u ON u.id = i.ubs_id
        JOIN medicamento m ON m.id = i.medicamento_id
        WHERE l.quantidade > 0 AND l.validade <= :ate AND ${escopo("i.ubs_id", ubsId)}
        ORDER BY l.validade, u.nome, m.nome, l.numero_lote
        LIMIT :limite
        """,
        params(ubsId).addValue("ate", ate).addValue("limite", limite),
    ) { rs, _ ->
        val validade = rs.data("validade")!!
        LoteVencendoResponse(
            loteId = rs.getLong("id"),
            numeroLote = rs.getString("numero_lote"),
            validade = validade,
            quantidade = rs.getInt("quantidade"),
            diasParaVencer = java.time.temporal.ChronoUnit.DAYS.between(hoje, validade),
            medicamento = MedicamentoResumoResponse(rs.getLong("medicamento_id"), nomeComConcentracao(rs)),
            ubs = UbsResumoResponse(rs.getLong("ubs_id"), rs.getString("ubs_nome")),
        )
    }

    fun ubsComSincronizacaoAtrasada(ubsId: Long?, limite: Instant, agora: Instant): List<SincronizacaoAtrasadaResponse> = jdbc.query(
        """
        SELECT u.id, u.nome, u.cnes, u.ultima_sincronizacao
        FROM ubs u
        WHERE (u.ultima_sincronizacao IS NULL OR u.ultima_sincronizacao < :limite) AND ${escopo("u.id", ubsId)}
        ORDER BY u.ultima_sincronizacao NULLS FIRST, u.nome
        """,
        params(ubsId).addValue("limite", Timestamp.from(limite)),
    ) { rs, _ ->
        val ultima = rs.instante("ultima_sincronizacao")
        SincronizacaoAtrasadaResponse(
            ubs = UbsEscopoResponse(rs.getLong("id"), rs.getString("nome"), rs.getString("cnes")),
            ultimaSincronizacao = ultima,
            atrasoHoras = ultima?.let { java.time.Duration.between(it, agora).toHours() },
        )
    }

    /**
     * Itens com saldo até o mínimo (em falta ou abaixo dele). A situação é PEDIDO_ENVIADO quando há remessa a caminho,
     * senão EM_FALTA (saldo zero) ou ABAIXO_MINIMO. Em falta primeiro, depois abaixo do mínimo e, por fim, os com
     * pedido enviado; em cada grupo, os com mais pacientes aguardando.
     */
    fun itensAtencao(ubsId: Long?, limiteGlobal: Int, limite: Int): List<ItemAtencaoResponse> = jdbc.query(
        """
        SELECT * FROM (
        SELECT i.ubs_id, u.nome AS ubs_nome, i.medicamento_id, m.nome AS medicamento_nome, m.concentracao,
               i.quantidade, COALESCE(m.limite_reservado, :limiteGlobal) AS minimo,
               (SELECT COUNT(DISTINCT w.dispositivo_id) FROM item_lista_desejo w
                WHERE w.ubs_id = i.ubs_id AND w.medicamento_id = i.medicamento_id) AS aguardando,
               (SELECT MIN(r.previsao_chegada) FROM remessa r
                WHERE r.ubs_id = i.ubs_id AND r.medicamento_id = i.medicamento_id AND $REMESSA_A_CAMINHO) AS remessa_prevista
        FROM item_estoque i
        JOIN ubs u ON u.id = i.ubs_id
        JOIN medicamento m ON m.id = i.medicamento_id
        WHERE i.quantidade <= COALESCE(m.limite_reservado, :limiteGlobal) AND ${escopo("i.ubs_id", ubsId)}
        ) atencao
        ORDER BY CASE WHEN remessa_prevista IS NOT NULL THEN 2 WHEN quantidade <= 0 THEN 0 ELSE 1 END,
                 aguardando DESC, quantidade, medicamento_nome, ubs_nome
        LIMIT :limite
        """,
        params(ubsId).addValue("limiteGlobal", limiteGlobal).addValue("limite", limite),
    ) { rs, _ ->
        val previsao = rs.data("remessa_prevista")
        val quantidade = rs.getInt("quantidade")
        ItemAtencaoResponse(
            ubs = UbsResumoResponse(rs.getLong("ubs_id"), rs.getString("ubs_nome")),
            medicamento = MedicamentoResumoResponse(rs.getLong("medicamento_id"), nomeComConcentracao(rs)),
            estoque = quantidade,
            minimo = rs.getInt("minimo"),
            aguardandoAviso = rs.getInt("aguardando"),
            situacao = when {
                previsao != null -> "PEDIDO_ENVIADO"
                quantidade <= 0 -> "EM_FALTA"
                else -> "ABAIXO_MINIMO"
            },
            remessaPrevista = previsao,
        )
    }

    private fun nomeComConcentracao(rs: ResultSet) = "${rs.getString("medicamento_nome")} ${rs.getString("concentracao")}"

    private companion object {
        const val REMESSA_EM_ANDAMENTO =
            "NOT EXISTS (SELECT 1 FROM remessa_evento e WHERE e.remessa_id = r.id AND e.etapa = 'DISPONIVEL')"
        const val REMESSA_A_CAMINHO =
            "NOT EXISTS (SELECT 1 FROM remessa_evento e WHERE e.remessa_id = r.id AND e.etapa IN ('RECEBIDO', 'DISPONIVEL'))"

        /** Mesma regra da disponibilidade de medicamentos: sem posição, sem status DISPONIVEL ou sem saldo. */
        const val NAO_DISPONIVEL = "(i.id IS NULL OR i.status <> 'DISPONIVEL' OR i.quantidade <= 0)"
    }
}
