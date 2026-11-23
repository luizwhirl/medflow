package br.ufal.medflow.perfil

import br.ufal.medflow.auth.CredenciaisInvalidasException
import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import br.ufal.medflow.common.ParametroInvalidoException
import br.ufal.medflow.common.RecursoNaoEncontradoException
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Consulta e atualização do perfil do cidadão autenticado.
 *
 * O ID do cidadão é obtido do JWT pelo controller.
 * Nenhuma operação permite escolher o proprietário do perfil.
 */
@Service
@Transactional(readOnly = true)
class PerfilCidadaoService(
    private val usuarios: UsuarioRepository,
    private val unidades: UbsRepository,
    private val buscas: BuscaRecenteUsuarioRepository,
) {

    /**
     * Consulta o perfil do cidadão.
     *
     * As buscas recentes só são retornadas quando
     * o armazenamento do histórico está habilitado.
     */
    fun consultar(usuarioId: Long): PerfilCidadaoResponse {
        val usuario = buscarUsuario(usuarioId)
        return montarResposta(usuario)
    }

    /**
     * Substitui os dados editáveis do perfil.
     *
     * As validações de tamanho, preenchimento e intervalos
     * numéricos são realizadas pelo AtualizarPerfilRequest.
     *
     * A atualização do usuário e do histórico de buscas
     * ocorre na mesma transação.
     */
    @Transactional
    fun atualizar(
        usuarioId: Long,
        request: AtualizarPerfilRequest,
    ): PerfilCidadaoResponse {
        val usuario = buscarUsuario(usuarioId)

        val nome = normalizarEspacos(request.nome)
        val bairro = normalizarBairro(request.bairro)

        val localizacao = validarLocalizacao(
            request.latitudeAproximada,
            request.longitudeAproximada,
        )

        val ubsReferencia = buscarUbsReferencia(
            request.ubsReferenciaId,
        )

        val termos = if (request.salvarBuscasRecentes) {
            normalizarBuscas(request.buscasRecentes)
        } else {
            emptyList()
        }

        usuario.nome = nome
        usuario.bairro = bairro
        usuario.latitudeAproximada = localizacao.first
        usuario.longitudeAproximada = localizacao.second
        usuario.ubsReferencia = ubsReferencia
        usuario.letraMaior = request.letraMaior
        usuario.salvarBuscasRecentes = request.salvarBuscasRecentes

        // Substitui o histórico apenas do cidadão autenticado.
        // A remoção e a inserção ocorrem na mesma transação.
        buscas.removerPorUsuario(usuarioId)

        if (termos.isNotEmpty()) {
            val registros = termos.mapIndexed { indice, termo ->
                BuscaRecenteUsuario(
                    id = BuscaRecenteUsuarioId(
                        usuarioId = usuarioId,
                        ordem = indice.toShort(),
                    ),
                    termo = termo,
                )
            }

            buscas.saveAll(registros)
        }

        // A entidade Usuario está gerenciada pelo JPA.
        // Suas alterações serão persistidas na transação.
        return montarResposta(usuario, termos)
    }

    /**
     * O usuário deve existir e corresponder ao ID do JWT.
     */
    private fun buscarUsuario(usuarioId: Long): Usuario =
        usuarios.findById(usuarioId).orElseThrow {
            CredenciaisInvalidasException()
        }

    /**
     * A UBS de referência é opcional.
     *
     * Sua existência é verificada diretamente no banco.
     * A validação de ID positivo já ocorre no DTO.
     */
    private fun buscarUbsReferencia(ubsId: Long?): Ubs? {
        if (ubsId == null) {
            return null
        }

        return unidades.findById(ubsId).orElseThrow {
            RecursoNaoEncontradoException("UBS", ubsId)
        }
    }

    /**
     * Remove espaços extras do bairro.
     *
     * Um valor vazio é armazenado como null.
     * O limite de caracteres é validado pelo DTO.
     */
    private fun normalizarBairro(bairro: String?): String? =
        bairro
            ?.let(::normalizarEspacos)
            ?.takeIf { it.isNotBlank() }

    /**
     * Latitude e longitude devem ser informadas juntas.
     *
     * A validação dos intervalos é feita pelo DTO.
     * O serviço verifica a combinação dos campos e
     * limita a precisão armazenada a duas casas decimais.
     */
    private fun validarLocalizacao(
        latitude: Double?,
        longitude: Double?,
    ): Pair<Double?, Double?> {
        if (latitude == null && longitude == null) {
            return null to null
        }

        if (latitude == null || longitude == null) {
            throw ParametroInvalidoException(
                "localizacaoAproximada",
                "Latitude e longitude devem ser informadas juntas",
            )
        }

        // Garante valores finitos antes do arredondamento.
        if (!latitude.isFinite() || !longitude.isFinite()) {
            throw ParametroInvalidoException(
                "localizacaoAproximada",
                "Latitude e longitude devem ser números finitos",
            )
        }

        return arredondarCoordenada(latitude) to
            arredondarCoordenada(longitude)
    }

    /**
     * Duas casas decimais oferecem precisão aproximada
     * na escala de 1 km, suficiente para sugerir UBS
     * próximas sem armazenar uma posição muito precisa.
     */
    private fun arredondarCoordenada(valor: Double): Double =
        BigDecimal.valueOf(valor)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()

    /**
     * Normaliza os termos e remove duplicações.
     *
     * O limite de 10 elementos é validado pelo DTO.
     * As regras de cada termo são verificadas aqui,
     * pois o DTO não possui validações individuais
     * para os elementos da lista.
     */
    private fun normalizarBuscas(
        termos: List<String>,
    ): List<String> {
        val normalizados = termos.map(::normalizarEspacos)

        if (normalizados.any { it.isBlank() }) {
            throw ParametroInvalidoException(
                "buscasRecentes",
                "O histórico não pode conter buscas vazias",
            )
        }

        if (normalizados.any { it.length > 120 }) {
            throw ParametroInvalidoException(
                "buscasRecentes",
                "Cada busca deve possuir no máximo 120 caracteres",
            )
        }

        return normalizados.distinctBy {
            it.lowercase(Locale.ROOT)
        }
    }

    private fun normalizarEspacos(valor: String): String =
        valor.trim().replace(Regex("\\s+"), " ")

    /**
     * Monta a resposta sem expor senha ou hash.
     *
     * Durante a atualização, os termos normalizados
     * são reutilizados sem consultar o banco novamente.
     */
    private fun montarResposta(
        usuario: Usuario,
        termosAtualizados: List<String>? = null,
    ): PerfilCidadaoResponse {
        val usuarioId = requireNotNull(usuario.id)

        val termos = if (!usuario.salvarBuscasRecentes) {
            emptyList()
        } else {
            termosAtualizados ?: buscas.listarPorUsuario(usuarioId)
                .map { it.termo }
        }

        return PerfilCidadaoResponse(
            id = usuarioId,
            nome = usuario.nome,
            email = usuario.email,
            ubsReferencia = usuario.ubsReferencia?.let { ubs ->
                UbsReferenciaResponse(
                    id = requireNotNull(ubs.id),
                    cnes = ubs.cnes,
                    nome = ubs.nome,
                    bairro = ubs.bairro,
                    endereco = ubs.endereco,
                )
            },
            bairro = usuario.bairro,
            latitudeAproximada = usuario.latitudeAproximada,
            longitudeAproximada = usuario.longitudeAproximada,
            letraMaior = usuario.letraMaior,
            salvarBuscasRecentes = usuario.salvarBuscasRecentes,
            buscasRecentes = termos,
        )
    }
}
