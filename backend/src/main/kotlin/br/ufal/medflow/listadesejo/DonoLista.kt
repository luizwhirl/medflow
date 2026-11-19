package br.ufal.medflow.listadesejo

import java.util.UUID

/** Quem é dono de uma lista de desejos: uma conta logada ou um aparelho sem login. */
sealed interface DonoLista {

    /** Cidadão autenticado, identificado pelo `sub` do JWT. */
    data class Conta(val usuarioId: Long) : DonoLista

    /** Aparelho sem login, identificado pelo UUID que o navegador gera e guarda. */
    data class Aparelho(val dispositivoId: UUID) : DonoLista
}
