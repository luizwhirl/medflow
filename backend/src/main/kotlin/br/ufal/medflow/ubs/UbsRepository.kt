package br.ufal.medflow.ubs

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UbsRepository : JpaRepository<Ubs, Long> {
    fun findAllByOrderByNomeAsc(): List<Ubs>

    /**
     * UBS da mais próxima para a mais distante, a partir de um ponto (fórmula de Haversine, em km, no banco).
     * Se houver estoque do medicamento na unidade, traz o status e a quantidade; senão vêm nulos.
     * Cada linha é `[Ubs, distanciaKm, status, quantidade]`. O ponto entra já em radianos (e o cosseno da
     * latitude), para a conta ficar só sobre as colunas.
     */
    @Query(
        """
        select u, $DISTANCIA_KM as distanciaKm, i.status, i.quantidade
        from Ubs u left join ItemEstoque i on i.ubs = u and i.medicamento.id = :medicamentoId
        where $DISTANCIA_KM <= :raioKm
        order by distanciaKm, u.nome
        """,
    )
    fun buscarProximas(
        @Param("latRad") latRad: Double,
        @Param("lngRad") lngRad: Double,
        @Param("cosLat") cosLat: Double,
        @Param("raioKm") raioKm: Double,
        @Param("medicamentoId") medicamentoId: Long,
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
