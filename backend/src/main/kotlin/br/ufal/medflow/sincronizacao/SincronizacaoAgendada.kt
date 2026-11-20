package br.ufal.medflow.sincronizacao

import br.ufal.medflow.aviso.AvisoAtrasoService
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
    private val avisoAtrasoService: AvisoAtrasoService,
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

    /**
     * Sincroniza cada município e verifica suas UBS com dados atrasados.
     *
     * A verificação de atrasos acontece mesmo se a consulta à BNAFAR falhar.
     * Falhas em um município ou na geração de avisos não impedem os demais.
     *
     * Se uma execução anterior ainda estiver em andamento, ignora a nova.
     */
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
                val resultado = try {
                    servico.sincronizar(municipio)
                } catch (e: Exception) {
                    log.error(
                        "Falha ao sincronizar o município {}: {}",
                        municipio,
                        e.message,
                        e,
                    )
                    null
                }

                // Verifica os dados atrasados mesmo quando a BNAFAR
                // está indisponível. Uma falha nos avisos não deve
                // impedir a sincronização de outros municípios.
                try {
                    val avisosCriados = avisoAtrasoService.verificarMunicipio(
                        municipio,
                    )

                    if (avisosCriados > 0) {
                        log.info(
                            "Município {}: {} avisos de dados atrasados criados",
                            municipio,
                            avisosCriados,
                        )
                    }
                } catch (e: Exception) {
                    log.error(
                        "Falha ao verificar avisos de atraso do município {}: {}",
                        municipio,
                        e.message,
                        e,
                    )
                }

                resultado
            }
        } finally {
            trava.unlock()
        }
    }
}
