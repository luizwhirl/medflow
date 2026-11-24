package br.ufal.medflow.aviso

import br.ufal.medflow.remessa.EtapaRemessa
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.time.LocalDate

enum class TipoAviso {
    /** O medicamento tem estoque disponível na UBS. */
    DISPONIVEL,

    /** Há uma remessa do medicamento a caminho da UBS (ainda não disponível). */
    REMESSA,

    /** A UBS está há mais de 24 h sem sincronizar (ou nunca sincronizou). */
    SINCRONIZACAO_ATRASADA,
}

/**
 * Aviso para quem acompanha medicamentos em uma UBS. O texto é montado pelo front a partir destes
 * campos. O `id` é estável para o mesmo acontecimento, para o front guardar o que já foi lido:
 * muda quando a remessa avança de etapa ou quando a UBS volta a atrasar depois de sincronizar.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AvisoResponse(
    val id: String,
    val tipo: TipoAviso,
    val ubsId: Long,
    val ubsNome: String,
    val medicamentoId: Long? = null,
    val medicamentoNome: String? = null,
    val medicamentoConcentracao: String? = null,
    val quantidade: Int? = null,
    val etapa: EtapaRemessa? = null,
    val previsaoChegada: LocalDate? = null,
    /** Quando aconteceu (atualização do estoque, etapa da remessa ou última sincronização). */
    val ocorridoEm: Instant? = null,
)
