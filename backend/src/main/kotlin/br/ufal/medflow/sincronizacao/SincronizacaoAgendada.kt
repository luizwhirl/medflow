package br.ufal.medflow.sincronizacao

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.locks.ReentrantLock

/** Roda a sincronização de cada município configurado. Só existe com `app.sync.ativo=true`. */
@Component
@ConditionalOnProperty("app.sync.ativo", havingValue = "true")
class SincronizacaoAgendada(
    private val servico: SincronizacaoEstoqueService,
    private val props: SincronizacaoProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val trava = ReentrantLock()

    @Scheduled(cron = "\${app.sync.cron}", zone = "America/Maceio")
    fun agendado() {
        executar()
    }

    @EventListener(ApplicationReadyEvent::class)
    fun aoIniciar() {
        if (props.executarAoIniciar) executar()
    }

    /** Uma falha em um município não impede os outros. Se já houver uma execução em andamento, ignora. */
    fun executar(): List<ResultadoSincronizacao> {
        if (props.municipios.isEmpty()) {
            log.warn("Sincronização ligada, mas app.sync.municipios está vazio")
            return emptyList()
        }
        if (!trava.tryLock()) {
            log.warn("Sincronização anterior ainda em andamento; execução ignorada")
            return emptyList()
        }
        try {
            return props.municipios.mapNotNull { municipio ->
                try {
                    servico.sincronizar(municipio)
                } catch (e: Exception) {
                    log.error("Falha ao sincronizar o município {}: {}", municipio, e.message, e)
                    null
                }
            }
        } finally {
            trava.unlock()
        }
    }
}
