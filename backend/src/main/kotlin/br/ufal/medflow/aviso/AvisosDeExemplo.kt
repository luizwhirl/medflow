package br.ufal.medflow.aviso

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Avisos fictícios para testar a interface no perfil dev.
 *
 * Executado após os ApplicationRunner, garantindo que
 * UsuariosDeExemplo já tenha criado a conta da Rita.
 *
 * Não é executado em produção ou nos testes com perfil test.
 */
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
                usuario_id,
                tipo,
                titulo,
                texto,
                lido,
                criado_em,
                chave_evento
            )
            SELECT
                u.id,
                exemplo.tipo,
                exemplo.titulo,
                exemplo.texto,
                exemplo.lido,
                NOW() - exemplo.idade,
                exemplo.chave_evento
            FROM usuario u
            CROSS JOIN (
                VALUES
                    (
                        'MEDICAMENTO_DISPONIVEL',
                        'Medicamento disponível (exemplo)',
                        'Aviso de demonstração: um medicamento acompanhado ficou disponível em uma UBS.',
                        FALSE,
                        INTERVAL '2 hours',
                        'dev:rita:disponibilidade'
                    ),
                    (
                        'UBS_DADOS_ATRASADOS',
                        'Dados da UBS desatualizados (exemplo)',
                        'Aviso de demonstração: uma UBS acompanhada está com os dados de estoque desatualizados.',
                        FALSE,
                        INTERVAL '1 day',
                        'dev:rita:atraso'
                    ),
                    (
                        'MEDICAMENTO_DISPONIVEL',
                        'Aviso já lido (exemplo)',
                        'Aviso de demonstração já marcado como lido, para testar o histórico.',
                        TRUE,
                        INTERVAL '3 days',
                        'dev:rita:aviso-lido'
                    )
            ) AS exemplo(
                tipo,
                titulo,
                texto,
                lido,
                idade,
                chave_evento
            )
            WHERE LOWER(u.email) = 'rita@medflow.local'
            ON CONFLICT ON CONSTRAINT uk_aviso_usuario_evento
            DO NOTHING
            """.trimIndent(),
        )

        log.info(
            "Avisos de exemplo do perfil dev: {} registros criados",
            inseridos,
        )
    }
}
