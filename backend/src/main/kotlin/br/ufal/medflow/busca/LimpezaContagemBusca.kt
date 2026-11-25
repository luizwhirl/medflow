package br.ufal.medflow.busca

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Apaga todo dia, às 3h de Maceió, as contagens de busca que passaram da retenção (LGPD). */
@Component
class LimpezaContagemBusca(private val contagemBuscaService: ContagemBuscaService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.buscas.cron-limpeza:0 0 3 * * *}", zone = "America/Maceio")
    fun limpar() {
        val apagadas = contagemBuscaService.apagarAntigas()
        if (apagadas > 0) log.info("Contagens de busca antigas apagadas: {}", apagadas)
    }
}
