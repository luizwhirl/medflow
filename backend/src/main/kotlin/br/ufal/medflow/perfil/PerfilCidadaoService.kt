package br.ufal.medflow.perfil

import br.ufal.medflow.auth.Usuario
import br.ufal.medflow.auth.UsuarioRepository
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Consulta e atualização do perfil do cidadão autenticado.
 *
 * O ID do cidadão será obtido do JWT pelo controller.
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
     * A atualização do usuário e do histórico de buscas
     * ocorre na mesma transação.
     */
    @Transactional
    fun atualizar(
        usuarioId: Long,
        request: AtualizarPerfilRequest,
    ): PerfilCidadaoResponse {
        val usuario = buscarUsuario(usuarioId)

        // Valida e normaliza os dados antes de modificar o perfil.
        val nome = normalizarNome(request.nome)
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

        // Atualiza o histórico somente do cidadão autenticado.
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

    private fun buscarUsuario(usuarioId: Long): Usuario =
        usuarios.findById(usuarioId).orElseThrow {
            ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Usuário autenticado não encontrado",
            )
        }

    /**
     * Consulta a UBS por ID.
     *
     * Uma UBS inexistente retorna 404.
     * Um ID não positivo retorna 400.
     */
    private fun buscarUbsReferencia(ubsId: Long?): Ubs? {
        if (ubsId == null) return null

        if (ubsId <= 0) {
            requisicaoInvalida(
                "O ID da UBS de referência deve ser positivo",
            )
        }

        return unidades.findById(ubsId).orElseThrow {
            ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "UBS $ubsId não encontrada",
            )
        }
    }

    private fun normalizarNome(nome: String): String {
        val normalizado = normalizarEspacos(nome)

        if (normalizado.isBlank()) {
            requisicaoInvalida("O nome não pode estar vazio")
        }

        if (normalizado.length > 120) {
            requisicaoInvalida(
                "O nome deve possuir no máximo 120 caracteres",
            )
        }

        return normalizado
    }

    private fun normalizarBairro(bairro: String?): String? {
        val normalizado = bairro
            ?.let(::normalizarEspacos)
            ?.takeIf { it.isNotBlank() }

        if (normalizado != null && normalizado.length > 120) {
            requisicaoInvalida(
                "O bairro deve possuir no máximo 120 caracteres",
            )
        }

        return normalizado
    }

    /**
     * Latitude e longitude devem ser informadas juntas.
     *
     * A precisão é limitada a três casas decimais para
     * representar uma localização aproximada.
     */
    private fun validarLocalizacao(
        latitude: Double?,
        longitude: Double?,
    ): Pair<Double?, Double?> {
        if (latitude == null && longitude == null) {
            return null to null
        }

        if (latitude == null || longitude == null) {
            requisicaoInvalida(
                "Latitude e longitude devem ser informadas juntas",
            )
        }

        if (!latitude.isFinite() || latitude !in -90.0..90.0) {
            requisicaoInvalida(
                "A latitude deve estar entre -90 e 90",
            )
        }

        if (!longitude.isFinite() || longitude !in -180.0..180.0) {
            requisicaoInvalida(
                "A longitude deve estar entre -180 e 180",
            )
        }

        return arredondarCoordenada(latitude) to
            arredondarCoordenada(longitude)
    }

    private fun arredondarCoordenada(valor: Double): Double =
        BigDecimal.valueOf(valor)
            .setScale(3, RoundingMode.HALF_UP)
            .toDouble()

    /**
     * Valida o histórico e remove termos duplicados.
     *
     * A primeira ocorrência de cada termo é preservada.
     * A comparação ignora diferenças entre maiúsculas
     * e minúsculas.
     */
    private fun normalizarBuscas(
        termos: List<String>,
    ): List<String> {
        if (termos.size > 10) {
            requisicaoInvalida(
                "O histórico deve conter no máximo 10 buscas",
            )
        }

        val normalizados = termos.map(::normalizarEspacos)

        if (normalizados.any { it.isBlank() }) {
            requisicaoInvalida(
                "O histórico não pode conter buscas vazias",
            )
        }

        if (normalizados.any { it.length > 120 }) {
            requisicaoInvalida(
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
     * Monta a resposta da API sem expor senha ou hash.
     *
     * Durante a atualização, os termos já normalizados
     * podem ser reutilizados sem consultar o banco novamente.
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

    private fun requisicaoInvalida(mensagem: String): Nothing =
        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            mensagem,
        )
}
