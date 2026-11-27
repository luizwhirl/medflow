package br.ufal.medflow.aviso

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/** Insere três avisos fictícios para um UUID público e fixo, apenas no perfil dev. */
@Component
@Profile("dev")
class AvisosDeExemplo(
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun criarAvisos() {
        val inseridos = jdbc.update(
            """
            INSERT INTO aviso (
                dispositivo_id, tipo, titulo, texto, lido, criado_em, chave_evento
            )
            SELECT
                '15da7427-97f6-4bf1-9d99-004af964e951'::uuid,
                exemplo.tipo,
                exemplo.titulo,
                exemplo.texto,
                exemplo.lido,
                NOW() - exemplo.idade,
                exemplo.chave_evento
            FROM (
                VALUES
                    (
                        'MEDICAMENTO_DISPONIVEL',
                        'Medicamento disponível (exemplo)',
                        'Aviso fictício de medicamento disponível para testar o frontend.',
                        FALSE,
                        INTERVAL '2 hours',
                        'dev:dispositivo:disponibilidade'
                    ),
                    (
                        'UBS_DADOS_ATRASADOS',
                        'Dados da UBS desatualizados (exemplo)',
                        'Aviso fictício de dados de estoque desatualizados.',
                        FALSE,
                        INTERVAL '1 day',
                        'dev:dispositivo:atraso'
                    ),
                    (
                        'MEDICAMENTO_DISPONIVEL',
                        'Aviso já lido (exemplo)',
                        'Aviso fictício já marcado como lido para testar o histórico.',
                        TRUE,
                        INTERVAL '3 days',
                        'dev:dispositivo:aviso-lido'
                    )
            ) AS exemplo(tipo, titulo, texto, lido, idade, chave_evento)
            ON CONFLICT ON CONSTRAINT uk_aviso_dispositivo_evento DO NOTHING
            """.trimIndent(),
        )
        log.info("Avisos de exemplo do perfil dev: {} registros criados", inseridos)
    }
}
