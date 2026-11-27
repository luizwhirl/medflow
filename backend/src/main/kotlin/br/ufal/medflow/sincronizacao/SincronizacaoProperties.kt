package br.ufal.medflow.sincronizacao

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.sync")
data class SincronizacaoProperties(
    /** Liga o job agendado. Desligado por padrão. */
    val ativo: Boolean = false,
    /** Municípios a sincronizar (IBGE com 6 ou 7 dígitos). */
    val municipios: List<String> = emptyList(),
    /** Roda também uma vez ao subir a aplicação (útil em desenvolvimento). */
    val executarAoIniciar: Boolean = false,
    /**
     * Quanto (km) uma UBS pode ficar fora do contorno do município no IBGE e ainda valer. A malha é simplificada e,
     * no litoral de Maceió, corta bairros como Ipioca e Riacho Doce a até 3,3 km; os erros reais da fonte passam de 4 km.
     */
    val toleranciaLimiteKm: Double = 3.5,
    /**
     * Quanto (km) a coordenada da fonte pode ficar do centro do bairro informado. Pega o erro que o contorno não pega:
     * a US Geraldo Melo (Bom Parto) vinha 29 km longe do bairro, dentro da tolerância do contorno; unidades certas de
     * bairros grandes do litoral (Ipioca, Riacho Doce) ficam a até ~5 km do centro.
     */
    val distanciaMaximaDoBairroKm: Double = 8.0,
)
