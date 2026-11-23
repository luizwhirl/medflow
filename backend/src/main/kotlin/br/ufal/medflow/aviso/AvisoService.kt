package br.ufal.medflow.aviso

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Consultas e marcação de leitura, sempre isoladas pelo UUID do dispositivo. */
@Service
@Transactional(readOnly = true)
class AvisoService(
    private val avisoRepository: AvisoRepository,
) {
    fun listar(dispositivoId: UUID): List<AvisoResponse> =
        avisoRepository.findTop50ByDispositivoIdOrderByCriadoEmDescIdDesc(dispositivoId)
            .map { it.toResponse() }

    fun contarNaoLidos(dispositivoId: UUID): AvisosNaoLidosResponse =
        AvisosNaoLidosResponse(
            naoLidos = avisoRepository.countByDispositivoIdAndLidoFalse(dispositivoId),
        )

    @Transactional
    fun marcarTodosComoLidos(dispositivoId: UUID): AvisosLidosResponse =
        AvisosLidosResponse(
            atualizados = avisoRepository.marcarTodosComoLidos(dispositivoId),
        )
}
