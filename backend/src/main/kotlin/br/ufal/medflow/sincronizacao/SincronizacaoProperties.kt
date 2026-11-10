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
)
