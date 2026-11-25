package br.ufal.medflow.ubs

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface UbsRepository : JpaRepository<Ubs, Long> {

    /** As que aparecem para o cidadão: todas, menos as sem localização confiável ([OrigemLocalizacao.INVALIDA]). */
    @Query("select u from Ubs u where u.localizacaoOrigem <> br.ufal.medflow.ubs.OrigemLocalizacao.INVALIDA order by u.nome")
    fun findVisiveis(): List<Ubs>

    @Query("select u from Ubs u where u.id = :id and u.localizacaoOrigem <> br.ufal.medflow.ubs.OrigemLocalizacao.INVALIDA")
    fun findVisivel(@Param("id") id: Long): Ubs?

    @Query("select count(u) from Ubs u where u.localizacaoOrigem <> br.ufal.medflow.ubs.OrigemLocalizacao.INVALIDA")
    fun contarVisiveis(): Long

    @Query("select count(u) from Ubs u where u.codigoIbgeMunicipio = :ibge and u.localizacaoOrigem <> br.ufal.medflow.ubs.OrigemLocalizacao.INVALIDA")
    fun contarVisiveisDoMunicipio(@Param("ibge") ibge: String): Long

    fun findByCnesIn(cnes: Collection<String>): List<Ubs>

    fun countByCodigoIbgeMunicipio(ibge: String): Long

    /**
     * Busca UBS de um município cujos dados estão atrasados.
     *
     * O limite é calculado pelo serviço a partir da configuração
     * de prazo máximo. Unidades sem data também são consideradas.
     *
     * O filtro ocorre diretamente no banco de dados.
     */
    @Query(
        """
        select u
        from Ubs u
        where u.codigoIbgeMunicipio = :codigoIbge
          and (
              u.ultimaSincronizacao is null
              or u.ultimaSincronizacao < :limite
          )
        order by u.id
        """,
    )
    fun buscarComDadosAtrasados(
        @Param("codigoIbge") codigoIbge: String,
        @Param("limite") limite: Instant,
    ): List<Ubs>

    /**
     * UBS da mais próxima para a mais distante, a partir de um ponto (fórmula de Haversine, em km, no banco).
     * Se houver estoque do medicamento na unidade, traz o status e a quantidade; senão vêm nulos
     * (com [medicamentoId] nulo, o join não casa com nada).
     *
     * A caixa de latitude/longitude ([CaixaGeografica]) descarta as unidades distantes pela comparação simples,
     * com apoio do índice `idx_ubs_localizacao`, antes da conta de distância.
     *
     * Cada linha é `[Ubs, distanciaKm, status, quantidade]`. O ponto entra já em radianos (e o cosseno da
     * latitude), para a conta ficar só sobre as colunas.
     */
    @Query(
        """
        select u, $DISTANCIA_KM as distanciaKm, i.status, i.quantidade
        from Ubs u left join ItemEstoque i on i.ubs = u and i.medicamento.id = :medicamentoId
        where u.localizacaoOrigem <> br.ufal.medflow.ubs.OrigemLocalizacao.INVALIDA
          and u.latitude between :latMin and :latMax
          and u.longitude between :lngMin and :lngMax
          and $DISTANCIA_KM <= :raioKm
        order by distanciaKm, u.nome
        """,
    )
    fun buscarProximas(
        @Param("latRad") latRad: Double,
        @Param("lngRad") lngRad: Double,
        @Param("cosLat") cosLat: Double,
        @Param("raioKm") raioKm: Double,
        @Param("latMin") latMin: Double,
        @Param("latMax") latMax: Double,
        @Param("lngMin") lngMin: Double,
        @Param("lngMax") lngMax: Double,
        @Param("medicamentoId") medicamentoId: Long?,
    ): List<Array<Any?>>

    companion object {
        /** Raio médio da Terra em km. */
        const val RAIO_DA_TERRA_KM = 6371.0

        private const val DISTANCIA_KM =
            "(2 * $RAIO_DA_TERRA_KM * asin(sqrt(least(1.0, " +
                "power(sin((radians(u.latitude) - :latRad) / 2), 2) + " +
                ":cosLat * cos(radians(u.latitude)) * power(sin((radians(u.longitude) - :lngRad) / 2), 2)))))"
    }
}
