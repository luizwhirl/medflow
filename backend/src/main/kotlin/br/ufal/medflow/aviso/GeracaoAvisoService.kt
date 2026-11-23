package br.ufal.medflow.aviso

import br.ufal.medflow.listadesejo.ItemListaDesejoRepository
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.ubs.Ubs
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Gera avisos vinculados aos dispositivos interessados durante a sincronização. */
@Service
class GeracaoAvisoService(
    private val listaDesejos: ItemListaDesejoRepository,
    private val avisos: AvisoRepository,
    private val relogio: Clock,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun registrarDisponibilidade(ubs: Ubs, medicamento: Medicamento): Int {
        val ubsId = requireNotNull(ubs.id) { "A UBS precisa estar persistida" }
        val medicamentoId = requireNotNull(medicamento.id) { "O medicamento precisa estar persistido" }

        val dispositivos = listaDesejos.buscarDispositivosParaDisponibilidade(ubsId, medicamentoId)
            .distinct()

        if (dispositivos.isEmpty()) return 0

        val prefixoEvento = "disponibilidade:$ubsId:$medicamentoId:"
        val comAvisoPendente = avisos.buscarDispositivosComDisponibilidadeNaoLida(
            dispositivos,
            TipoAviso.MEDICAMENTO_DISPONIVEL,
            prefixoEvento,
        ).toSet()

        val paraNotificar = dispositivos.filterNot { it in comAvisoPendente }
        if (paraNotificar.isEmpty()) return 0

        val chaveEvento = "$prefixoEvento${UUID.randomUUID()}"
        val criadoEm = Instant.now(relogio)

        avisos.saveAll(
            paraNotificar.map { dispositivo ->
                Aviso(
                    dispositivoId = dispositivo,
                    tipo = TipoAviso.MEDICAMENTO_DISPONIVEL,
                    titulo = "Medicamento disponível",
                    texto = "O medicamento ${medicamento.nome} está disponível na unidade ${ubs.nome}.",
                    lido = false,
                    criadoEm = criadoEm,
                    chaveEvento = chaveEvento,
                )
            },
        )
        return paraNotificar.size
    }
}
