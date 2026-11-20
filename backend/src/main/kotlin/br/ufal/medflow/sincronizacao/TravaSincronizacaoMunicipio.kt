package br.ufal.medflow.sincronizacao

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCallback
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Impede que duas transações sincronizem simultaneamente
 * o estoque do mesmo município.
 *
 * A trava pertence à transação do PostgreSQL e é liberada
 * automaticamente no COMMIT ou ROLLBACK.
 */
@Component
class TravaSincronizacaoMunicipio(
    private val jdbc: JdbcTemplate,
) {

    fun adquirir(codigoIbge: String) {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "A trava de sincronização exige uma transação ativa"
        }

        require(CODIGO_IBGE.matches(codigoIbge)) {
            "O código IBGE deve conter 6 ou 7 dígitos"
        }

        // A primeira chave identifica o domínio de sincronização.
        // A segunda identifica o município pelo código IBGE.
        //
        // O PostgreSQL aguarda caso outra transação já esteja
        // sincronizando esse mesmo município.
        jdbc.execute(
            "SELECT pg_advisory_xact_lock(350035, CAST(? AS INTEGER))",
            PreparedStatementCallback { statement ->
                statement.setString(1, codigoIbge)
                statement.execute()
                Unit
            },
        )
    }

    private companion object {
        val CODIGO_IBGE = Regex("\\d{6,7}")
    }
}
