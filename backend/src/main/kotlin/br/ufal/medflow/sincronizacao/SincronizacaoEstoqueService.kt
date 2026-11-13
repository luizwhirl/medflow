package br.ufal.medflow.sincronizacao

import br.ufal.medflow.estoque.ItemEstoque
import br.ufal.medflow.estoque.ItemEstoqueRepository
import br.ufal.medflow.estoque.LoteEstoque
import br.ufal.medflow.estoque.LoteEstoqueRepository
import br.ufal.medflow.estoque.RegraStatusEstoque
import br.ufal.medflow.estoque.StatusEstoque
import br.ufal.medflow.integracao.FonteEstoque
import br.ufal.medflow.integracao.PosicaoEstoque
import br.ufal.medflow.medicamento.Medicamento
import br.ufal.medflow.medicamento.MedicamentoRepository
import br.ufal.medflow.ubs.TipoUnidade
import br.ufal.medflow.ubs.Ubs
import br.ufal.medflow.ubs.UbsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Traz o estoque de um município da fonte (BNAFAR) para o banco: UBS, medicamentos, itens e lotes.
 * Idempotente: rodar de novo com os mesmos dados não muda nada.
 */
@Service
class SincronizacaoEstoqueService(
    private val fonte: FonteEstoque,
    private val ubsRepository: UbsRepository,
    private val medicamentoRepository: MedicamentoRepository,
    private val itemRepository: ItemEstoqueRepository,
    private val loteRepository: LoteEstoqueRepository,
    private val regraStatus: RegraStatusEstoque,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val transacao = TransactionTemplate(transactionManager)

    /** @throws br.ufal.medflow.integracao.FonteEstoqueIndisponivelException se a fonte falhar (nada é gravado) */
    fun sincronizar(codigoIbge: String): ResultadoSincronizacao {
        val posicoes = fonte.buscarPosicoes(codigoIbge) // fora da transação: a chamada HTTP pode demorar
        val resultado = transacao.execute { gravar(codigoIbge, posicoes) }
        log.info("Sincronização {}: {}", codigoIbge, resultado)
        return resultado
    }

    private data class Valida(
        val posicao: PosicaoEstoque,
        val tipo: TipoUnidade,
        val produto: ProdutoDescrito,
        val catmat: String,
    )

    private fun gravar(codigoIbge: String, posicoes: List<PosicaoEstoque>): ResultadoSincronizacao {
        var naoUbs = 0
        var semCoordenadas = 0
        var naoMedicamento = 0
        val validas = mutableListOf<Valida>()
        for (p in posicoes) {
            val tipo = ClassificacaoUnidade.tipo(p.nomeUnidade)
            when {
                tipo == null -> naoUbs++
                p.latitude == null || p.longitude == null -> semCoordenadas++
                else -> {
                    val produto = DescricaoProduto.analisar(p.descricaoProduto)
                    if (produto == null) naoMedicamento++ else validas += Valida(p, tipo, produto, Catmat.base(p.catmat))
                }
            }
        }

        // Por item vale só a posição mais recente: se a fonte devolver mais de um dia, não soma entre datas.
        val porItem = validas.groupBy { it.posicao.cnes to it.catmat }.mapValues { (_, lista) ->
            val maisRecente = lista.maxOf { it.posicao.dataPosicao }
            lista.filter { it.posicao.dataPosicao == maisRecente }
        }
        val referenciaPorUbs = validas.groupBy { it.posicao.cnes }
            .mapValues { (_, lista) -> lista.maxOf { it.posicao.dataPosicao }.comoInstante() }

        val ubsPorCnes = gravarUnidades(validas)
        val medicamentoPorCatmat = gravarMedicamentos(validas)
        val itens = gravarItens(porItem, referenciaPorUbs, ubsPorCnes, medicamentoPorCatmat)
        val lotes = gravarLotes(porItem, itens.porChave)

        return ResultadoSincronizacao(
            codigoIbge = codigoIbge,
            posicoesLidas = posicoes.size,
            descartes = Descartes(naoUbs, semCoordenadas, naoMedicamento),
            unidades = ubsPorCnes.contagem,
            medicamentos = medicamentoPorCatmat.contagem,
            itens = itens.contagem,
            lotes = lotes,
        )
    }

    private class Gravados<K, T>(val porChave: Map<K, T>, val contagem: Contagem)

    private fun gravarUnidades(validas: List<Valida>): Gravados<String, Ubs> {
        val porCnes = validas.groupBy { it.posicao.cnes }
        val existentes = ubsRepository.findByCnesIn(porCnes.keys).associateBy { it.cnes }
        val resultado = HashMap<String, Ubs>()
        val novas = mutableListOf<Ubs>()
        var atualizadas = 0

        for ((cnes, lista) in porCnes) {
            val maisRecente = lista.maxBy { it.posicao.dataPosicao }
            val candidata = novaUbs(maisRecente, lista.maxOf { it.posicao.dataPosicao }.comoInstante())
            val existente = existentes[cnes]
            if (existente == null) {
                novas += candidata
            } else {
                if (existente.atualizarCom(candidata)) atualizadas++
                resultado[cnes] = existente
            }
        }
        ubsRepository.saveAll(novas).forEach { resultado[it.cnes] = it }
        return Gravados(resultado, Contagem(criados = novas.size, atualizados = atualizadas))
    }

    private fun novaUbs(v: Valida, ultimaSincronizacao: Instant): Ubs {
        val p = v.posicao
        return Ubs(
            cnes = p.cnes,
            nome = Nomes.proprio(p.nomeUnidade ?: "Unidade ${p.cnes}").limitar(160),
            tipo = v.tipo,
            bairro = Nomes.proprio(p.bairro ?: "Não informado").limitar(120),
            endereco = enderecoDe(p).limitar(200),
            telefone = p.telefone?.limitar(20),
            codigoIbgeMunicipio = p.codigoIbgeMunicipio,
            latitude = p.latitude!!,
            longitude = p.longitude!!,
            ultimaSincronizacao = ultimaSincronizacao,
        )
    }

    private fun enderecoDe(p: PosicaoEstoque): String {
        val partes = listOfNotNull(p.logradouro?.takeIf { it.isNotBlank() }, p.numero?.takeIf { it.isNotBlank() }).map(Nomes::proprio)
        return if (partes.isEmpty()) "Endereço não informado" else partes.joinToString(", ")
    }

    /** O horário de funcionamento não vem da fonte, então não é tocado. */
    private fun Ubs.atualizarCom(n: Ubs): Boolean {
        val mudou = nome != n.nome || tipo != n.tipo || bairro != n.bairro || endereco != n.endereco ||
            telefone != n.telefone || codigoIbgeMunicipio != n.codigoIbgeMunicipio || latitude != n.latitude ||
            longitude != n.longitude || ultimaSincronizacao != n.ultimaSincronizacao
        if (mudou) {
            nome = n.nome; tipo = n.tipo; bairro = n.bairro; endereco = n.endereco; telefone = n.telefone
            codigoIbgeMunicipio = n.codigoIbgeMunicipio; latitude = n.latitude; longitude = n.longitude
            ultimaSincronizacao = n.ultimaSincronizacao
        }
        return mudou
    }

    /** Medicamento que já existe (inclusive do seed) não é sobrescrito. */
    private fun gravarMedicamentos(validas: List<Valida>): Gravados<String, Medicamento> {
        val porCatmat = validas.groupBy { it.catmat }
        val resultado = HashMap<String, Medicamento>()
        medicamentoRepository.findByCatmatIn(porCatmat.keys).forEach { resultado[it.catmat] = it }

        val novos = porCatmat.filterKeys { it !in resultado }.map { (catmat, lista) ->
            val produto = lista.minBy { it.posicao.descricaoProduto.orEmpty() }.produto
            Medicamento(
                catmat = catmat.limitar(20),
                nome = produto.nome.limitar(160),
                concentracao = produto.concentracao.limitar(60),
                formaFarmaceutica = produto.forma.limitar(60),
                categoria = CategoriaMedicamento.de(produto.nome),
                rename = false,
            )
        }
        medicamentoRepository.saveAll(novos).forEach { resultado[it.catmat] = it }
        return Gravados(resultado, Contagem(criados = novos.size))
    }

    private class Itens(val porChave: Map<Pair<String, String>, ItemEstoque>, val contagem: Contagem)

    /**
     * Item que a unidade deixou de informar (o estoque acabou) vai para 0 / EM_FALTA, em vez de ficar com o saldo
     * antigo. Vale só para as unidades que vieram na resposta: unidade que sumiu inteira não é tocada, e a data de
     * sincronização dela envelhece até aparecer como atrasada.
     */
    private fun gravarItens(
        porItem: Map<Pair<String, String>, List<Valida>>,
        referenciaPorUbs: Map<String, Instant>,
        ubsPorCnes: Gravados<String, Ubs>,
        medicamentoPorCatmat: Gravados<String, Medicamento>,
    ): Itens {
        val existentes = itemRepository.findDasUbs(ubsPorCnes.porChave.values.map { it.id!! })
            .associateBy { it.ubs.cnes to it.medicamento.catmat }
        val resultado = HashMap<Pair<String, String>, ItemEstoque>()
        val novos = mutableListOf<ItemEstoque>()
        var atualizados = 0

        for ((chave, lista) in porItem) {
            val quantidade = lista.sumOf { it.posicao.quantidadeInteira().toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val status = regraStatus.calcular(quantidade)
            val atualizadoEm = lista.maxOf { it.posicao.dataPosicao }.comoInstante()

            val existente = existentes[chave]
            if (existente == null) {
                novos += ItemEstoque(ubsPorCnes.porChave.getValue(chave.first), medicamentoPorCatmat.porChave.getValue(chave.second), status, quantidade, atualizadoEm)
            } else {
                if (existente.quantidade != quantidade || existente.status != status || existente.atualizadoEm != atualizadoEm) {
                    existente.quantidade = quantidade
                    existente.status = status
                    existente.atualizadoEm = atualizadoEm
                    atualizados++
                }
                resultado[chave] = existente
            }
        }
        for ((chave, existente) in existentes) {
            if (chave in porItem) continue
            resultado[chave] = existente
            if (existente.quantidade != 0 || existente.status != StatusEstoque.EM_FALTA) {
                existente.quantidade = 0
                existente.status = regraStatus.calcular(0)
                existente.atualizadoEm = referenciaPorUbs.getValue(chave.first)
                atualizados++
            }
        }
        itemRepository.saveAll(novos).forEach { resultado[it.ubs.cnes to it.medicamento.catmat] = it }
        return Itens(resultado, Contagem(criados = novos.size, atualizados = atualizados))
    }

    private data class LoteDesejado(val validade: LocalDate, val quantidade: Int)

    /**
     * Só vira lote o que tem validade e saldo; lotes repetidos no mesmo item somam e ficam com a menor validade.
     * Item que sumiu da fonte (já zerado) perde todos os lotes. Por isso a soma dos lotes pode ser menor que a
     * quantidade do item: posição sem validade conta no total, mas não vira lote.
     */
    private fun gravarLotes(
        porItem: Map<Pair<String, String>, List<Valida>>,
        itens: Map<Pair<String, String>, ItemEstoque>,
    ): Contagem {
        val existentes = loteRepository.findDosItens(itens.values.map { it.id!! }).groupBy { it.itemEstoque.id!! }
        val novos = mutableListOf<LoteEstoque>()
        val remover = mutableListOf<LoteEstoque>()
        var atualizados = 0

        for ((chave, item) in itens) {
            val desejados = porItem[chave].orEmpty()
                .filter { it.posicao.validade != null && it.posicao.quantidadeInteira() > 0 }
                .groupBy { it.posicao.numeroLote?.trim()?.takeIf(String::isNotEmpty)?.limitar(40) ?: "S/L" }
                .mapValues { (_, g) ->
                    LoteDesejado(g.minOf { it.posicao.validade!! }, g.sumOf { it.posicao.quantidadeInteira() })
                }
            val atuais = existentes[item.id!!].orEmpty().associateBy { it.numeroLote }

            desejados.forEach { (numero, d) ->
                val atual = atuais[numero]
                when {
                    atual == null -> novos += LoteEstoque(item, numero, d.validade, d.quantidade)
                    atual.validade != d.validade || atual.quantidade != d.quantidade -> {
                        atual.validade = d.validade
                        atual.quantidade = d.quantidade
                        atualizados++
                    }
                }
            }
            remover += atuais.filterKeys { it !in desejados }.values
        }
        loteRepository.saveAll(novos)
        loteRepository.deleteAll(remover)
        return Contagem(criados = novos.size, atualizados = atualizados, removidos = remover.size)
    }

    private fun PosicaoEstoque.quantidadeInteira(): Int =
        quantidade.setScale(0, RoundingMode.DOWN).toLong().coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    /** O dia da posição vira o instante de início desse dia em Maceió. */
    private fun LocalDate.comoInstante(): Instant = atStartOfDay(FUSO).toInstant()

    private fun String.limitar(max: Int) = if (length <= max) this else take(max)

    private companion object {
        val FUSO: ZoneId = ZoneId.of("America/Maceio")
    }
}
