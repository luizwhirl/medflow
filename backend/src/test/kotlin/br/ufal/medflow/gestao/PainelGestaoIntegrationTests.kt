package br.ufal.medflow.gestao

import br.ufal.medflow.TestcontainersConfiguration
import br.ufal.medflow.auth.JwtService
import br.ufal.medflow.auth.UsuarioRepository
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Perfil "dev". Cada teste monta, dentro da sua transação, uma cidade fictícia (IBGE 9999999) com as UBS A, B e C e
 * pede os números só da UBS A (`ubsId`), então os valores esperados não dependem do seed. Os testes da rede toda
 * conferem contra consultas simples e independentes.
 *
 * Medicamentos: X (mínimo 10), Y (sem limite próprio: vale o global, 20), Z (mínimo 10) e W (sem posição na UBS A).
 * UBS A: X 0 (em falta), Y 15, Z 50. UBS B: X 45, Y 40. UBS C: X 30, Y 100.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PainelGestaoIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val usuarios: UsuarioRepository,
    @Autowired private val jwtService: JwtService,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val hoje: LocalDate = LocalDate.now(ZoneId.of("America/Maceio"))

    private class Cenario(val a: Long, val b: Long, val c: Long, val x: Long, val y: Long, val z: Long, val w: Long)

    private fun token(email: String) = jwtService.emitir(requireNotNull(usuarios.findByEmail(email))).token

    /** Posição da BNAFAR de [diasAtras] dias atrás: a data gravada à meia-noite em Maceió (0 = hoje). */
    private fun meiaNoite(diasAtras: Long): String =
        "'${hoje.minusDays(diasAtras).atStartOfDay(ZoneId.of("America/Maceio")).toInstant()}'::timestamptz"

    private fun id(sql: String, vararg args: Any?) = jdbc.queryForObject(sql, Long::class.java, *args)!!

    private fun ubs(cnes: String, nome: String, lat: Double, lng: Double, ibge: String = "9999999", ultima: String = "NOW()") = id(
        "INSERT INTO ubs (cnes, nome, tipo, bairro, endereco, codigo_ibge_municipio, latitude, longitude, ultima_sincronizacao) " +
            "VALUES (?, ?, 'UBS', 'Centro', 'Rua A, 1', ?, ?, ?, $ultima) RETURNING id",
        cnes, nome, ibge, lat, lng,
    )

    private fun medicamento(catmat: String, nome: String, limite: Int?) = id(
        "INSERT INTO medicamento (catmat, nome, concentracao, forma_farmaceutica, categoria, limite_reservado) " +
            "VALUES (?, ?, '10 mg', 'comprimido', 'Teste', ?) RETURNING id",
        catmat, nome, limite,
    )

    private fun item(ubs: Long, medicamento: Long, quantidade: Int, status: String? = null) = id(
        "INSERT INTO item_estoque (ubs_id, medicamento_id, status, quantidade) VALUES (?, ?, ?, ?) RETURNING id",
        ubs, medicamento, status ?: if (quantidade == 0) "EM_FALTA" else if (quantidade <= 20) "RESERVADO" else "DISPONIVEL", quantidade,
    )

    private fun remessa(ubs: Long, medicamento: Long, previsaoEmDias: Long, concluida: Boolean, recebida: Boolean = false) {
        val remessa = id(
            "INSERT INTO remessa (ubs_id, medicamento_id, quantidade, previsao_chegada) VALUES (?, ?, 10, ?) RETURNING id",
            ubs, medicamento, hoje.plusDays(previsaoEmDias),
        )
        jdbc.update("INSERT INTO remessa_evento (remessa_id, etapa, ocorrido_em) VALUES (?, 'PEDIDO', NOW())", remessa)
        if (recebida || concluida) jdbc.update("INSERT INTO remessa_evento (remessa_id, etapa, ocorrido_em) VALUES (?, 'RECEBIDO', NOW())", remessa)
        if (concluida) jdbc.update("INSERT INTO remessa_evento (remessa_id, etapa, ocorrido_em) VALUES (?, 'DISPONIVEL', NOW())", remessa)
    }

    private fun lote(item: Long, numero: String, validadeEmDias: Long, quantidade: Int) = jdbc.update(
        "INSERT INTO lote_estoque (item_estoque_id, numero_lote, validade, quantidade) VALUES (?, ?, ?, ?)",
        item, numero, hoje.plusDays(validadeEmDias), quantidade,
    )

    /** Sem login, o paciente é um aparelho: um UUID fixo por nome, para os testes lerem bem. */
    private fun paciente(nome: String): UUID = UUID.nameUUIDFromBytes(nome.toByteArray())

    private fun desejo(aparelho: UUID, ubs: Long, medicamento: Long) = jdbc.update(
        "INSERT INTO item_lista_desejo (dispositivo_id, ubs_id, medicamento_id) VALUES (?, ?, ?)", aparelho, ubs, medicamento,
    )

    private fun cenario(): Cenario {
        val a = ubs("9990001", "UBS Teste A", -9.60, -35.70, ultima = meiaNoite(2))
        val b = ubs("9990002", "UBS Teste B", -9.61, -35.70, ultima = meiaNoite(1))
        val c = ubs("9990003", "UBS Teste C", -9.70, -35.70, ultima = meiaNoite(0))
        // outra cidade, a 110 m da A, com sobra de X: não pode ser origem de remanejamento
        val d = ubs("9990004", "UBS Teste D", -9.601, -35.70, ibge = "9999998")
        val x = medicamento("TESTE-X", "Alfa X", 10)
        val y = medicamento("TESTE-Y", "Beta Y", null)
        val z = medicamento("TESTE-Z", "Gama Z", 10)
        val w = medicamento("TESTE-W", "Delta W", null)

        item(a, x, 0)
        item(a, y, 15)
        val itemAZ = item(a, z, 50)
        item(b, x, 45)
        item(b, y, 40) // exatamente o dobro do mínimo: não é sobra
        item(c, x, 30)
        item(c, y, 100)
        item(d, x, 500)

        remessa(a, x, previsaoEmDias = 3, concluida = false)
        remessa(a, y, previsaoEmDias = 1, concluida = true)

        lote(itemAZ, "VENCIDO", -5, 3)
        lote(itemAZ, "EM30", 30, 5)
        lote(itemAZ, "EM90", 90, 7)
        lote(itemAZ, "SEMSALDO", 10, 0)

        val u1 = paciente("Ana")
        val u2 = paciente("Bia")
        val u3 = paciente("Caio")
        desejo(u1, a, x)
        desejo(u2, a, x)
        desejo(u1, a, y)
        desejo(u3, a, z) // Z está disponível na A: não é demanda reprimida
        desejo(u3, a, w) // W não tem posição na A
        return Cenario(a, b, c, x, y, z, w)
    }

    /**
     * UBS E (cidade fictícia), todos os medicamentos com mínimo 10:
     * P 0 com remessa a caminho (+3 dias) · Q 0 sem remessa, esperado por Duda e Edu · R 7 · S 11 (acima do mínimo) ·
     * T 0 cuja única remessa já está disponível · U 0 com status DISPONIVEL, esperado por Fabi · V 0 com remessa só recebida ·
     * X 5 com remessa a caminho atrasada (-2 dias).
     */
    private fun cenarioAtencao(): Long {
        val e = ubs("9990005", "UBS Teste E", -9.8, -35.9)
        val meds = listOf("P", "Q", "R", "S", "T", "U", "V", "X").associateWith { medicamento("ATE-$it", "Med $it", 10) }
        item(e, meds.getValue("P"), 0)
        item(e, meds.getValue("Q"), 0)
        item(e, meds.getValue("R"), 7)
        item(e, meds.getValue("S"), 11)
        item(e, meds.getValue("T"), 0)
        item(e, meds.getValue("U"), 0, status = "DISPONIVEL")
        item(e, meds.getValue("V"), 0)
        item(e, meds.getValue("X"), 5)
        remessa(e, meds.getValue("P"), previsaoEmDias = 3, concluida = false)
        remessa(e, meds.getValue("T"), previsaoEmDias = -1, concluida = true)
        remessa(e, meds.getValue("V"), previsaoEmDias = 1, concluida = false, recebida = true)
        remessa(e, meds.getValue("X"), previsaoEmDias = -2, concluida = false)
        val (duda, edu, fabi) = Triple(paciente("Duda"), paciente("Edu"), paciente("Fabi"))
        desejo(duda, e, meds.getValue("Q"))
        desejo(edu, e, meds.getValue("Q"))
        desejo(fabi, e, meds.getValue("U"))
        return e
    }

    private fun pedir(rota: String, email: String? = "marta@medflow.local", ubsId: Long? = null, parametros: Array<Pair<String, String>> = emptyArray()): ResultActionsDsl =
        mockMvc.get("/api/gestao/$rota") {
            email?.let { header("Authorization", "Bearer ${token(it)}") }
            ubsId?.let { param("ubsId", it.toString()) }
            parametros.forEach { (k, v) -> param(k, v) }
        }

    private fun distanciaKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val (p1, p2, dl) = Triple(Math.toRadians(lat1), Math.toRadians(lat2), Math.toRadians(lng2 - lng1))
        return acos(sin(p1) * sin(p2) + cos(p1) * cos(p2) * cos(dl)) * 6371.0
    }

    @Test
    fun `indicadores da UBS A batem com o cenario montado`() {
        val c = cenario()

        pedir("indicadores", ubsId = c.a).andExpect {
            status { isOk() }
            jsonPath("$.ubs.cnes") { value("9990001") }
            jsonPath("$.restrito") { value(false) }
            jsonPath("$.itensEmFalta") { value(1) } // X
            jsonPath("$.emFaltaComRemessa") { value(1) } // X tem remessa em andamento
            jsonPath("$.abaixoDoMinimo") { value(1) } // Y: 15 <= 20 (global); Z: 50 > 10
            jsonPath("$.remessasACaminho") { value(1) } // a de Y já chegou
            jsonPath("$.remessasAtrasadas") { value(0) }
            jsonPath("$.proximaRemessa") { value(hoje.plusDays(3).toString()) }
            jsonPath("$.pacientesAguardando") { value(3) } // Ana e Bia (X), Ana (Y), Caio (W); Caio em Z não conta
            jsonPath("$.medicamentosAguardados") { value(3) } // X, Y e W
            jsonPath("$.lotesVencendo") { value(2) } // VENCIDO e EM30; EM90 e SEMSALDO ficam de fora
            jsonPath("$.ubsComSincronizacaoAtrasada") { value(1) } // só a A, com 2 dias
            jsonPath("$.ultimaSincronizacao") { isNotEmpty() }
        }
    }

    @Test
    fun `indicadores da rede batem com contagens simples sobre o banco`() {
        cenario()

        pedir("indicadores").andExpect {
            status { isOk() }
            jsonPath("$.ubs") { value(nullValue()) }
            jsonPath("$.restrito") { value(false) }
            jsonPath("$.itensEmFalta") { value(id("SELECT COUNT(*) FROM item_estoque WHERE quantidade = 0").toInt()) }
            jsonPath("$.remessasACaminho") {
                value(
                    id(
                        "SELECT COUNT(*) FROM remessa r LEFT JOIN remessa_evento e ON e.remessa_id = r.id AND e.etapa IN ('RECEBIDO', 'DISPONIVEL') WHERE e.id IS NULL",
                    ).toInt(),
                )
            }
            jsonPath("$.lotesVencendo") {
                value(
                    id("SELECT COUNT(*) FROM lote_estoque WHERE quantidade > 0 AND validade <= ?", hoje.plusDays(60)).toInt(),
                )
            }
        }
    }

    @Test
    fun `remanejamento sugere da UBS mais proxima da mesma cidade e respeita o dobro do minimo`() {
        val c = cenario()
        val kmAB = distanciaKm(-9.60, -35.70, -9.61, -35.70)
        val kmAC = distanciaKm(-9.60, -35.70, -9.70, -35.70)

        pedir("remanejamento", ubsId = c.a).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            // em falta primeiro: X precisa de 2*10 - 0 = 20; B (45) tem sobra 25 e está mais perto que C (30). Pede 20 de B.
            jsonPath("$[0].medicamento.id") { value(c.x) }
            jsonPath("$[0].medicamento.nome") { value("Alfa X 10 mg") }
            jsonPath("$[0].destino.id") { value(c.a) }
            jsonPath("$[0].quantidadeDestino") { value(0) }
            jsonPath("$[0].minimo") { value(10) }
            jsonPath("$[0].origem.id") { value(c.b) }
            jsonPath("$[0].quantidadeOrigem") { value(45) }
            jsonPath("$[0].quantidadeSugerida") { value(20) }
            // Y precisa de 2*20 - 15 = 25; B tem 40 (não passa do dobro), então vem de C (100, sobra 60)
            jsonPath("$[1].medicamento.id") { value(c.y) }
            jsonPath("$[1].origem.id") { value(c.c) }
            jsonPath("$[1].quantidadeSugerida") { value(25) }
        }.andReturn().response.contentAsString.let { corpo ->
            val raiz = com.fasterxml.jackson.databind.ObjectMapper().readTree(corpo)
            assertEquals(kmAB, raiz[0]["distanciaKm"].asDouble(), 0.01)
            assertEquals(kmAC, raiz[1]["distanciaKm"].asDouble(), 0.01)
        }
    }

    @Test
    fun `remanejamento nao usa UBS de outra cidade como origem nem sugere para quem nao precisa`() {
        val c = cenario()

        pedir("remanejamento", ubsId = c.a).andExpect {
            jsonPath("$[*].origem.nome") { value(org.hamcrest.Matchers.not(hasItem("UBS Teste D"))) }
            jsonPath("$[*].medicamento.id") { value(org.hamcrest.Matchers.not(hasItem(c.z))) } // Z (50 > 10) não precisa
        }
    }

    @Test
    fun `limite do remanejamento corta a lista mantendo o em falta primeiro`() {
        val c = cenario()

        pedir("remanejamento", ubsId = c.a, parametros = arrayOf("limite" to "1")).andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].medicamento.id") { value(c.x) }
        }
    }

    @Test
    fun `demanda reprimida conta pacientes distintos onde o item nao esta disponivel`() {
        val c = cenario()

        pedir("demanda-reprimida", ubsId = c.a).andExpect {
            status { isOk() }
            jsonPath("$.itens.length()") { value(3) } // X, Y e W; Z está disponível
            jsonPath("$.itens[0].medicamento.id") { value(c.x) }
            jsonPath("$.itens[0].pacientesAguardando") { value(2) }
            jsonPath("$.itens[0].status") { value("EM_FALTA") }
            jsonPath("$.itens[0].quantidade") { value(0) }
            jsonPath("$.itens[1].medicamento.id") { value(c.y) } // empate em 1 paciente: por nome (Beta antes de Delta)
            jsonPath("$.itens[1].status") { value("RESERVADO") }
            jsonPath("$.itens[2].medicamento.id") { value(c.w) }
            jsonPath("$.itens[2].status") { value(nullValue()) } // sem posição na UBS
            jsonPath("$.itens[2].quantidade") { value(nullValue()) }
        }
    }

    private fun buscas(ubs: Long?, medicamento: Long, diasAtras: Long, total: Int) = jdbc.update(
        "INSERT INTO contagem_busca (dia, medicamento_id, ubs_id, total) VALUES (?, ?, ?, ?)",
        hoje.minusDays(diasAtras), medicamento, ubs, total,
    )

    @Test
    fun `mais procurados somam as buscas dos ultimos 7 dias da UBS, e nao a lista de desejos`() {
        val c = cenario()
        buscas(c.a, c.z, diasAtras = 0, total = 3)
        buscas(c.a, c.z, diasAtras = 6, total = 4) // sétimo dia contando hoje: ainda entra
        buscas(c.a, c.z, diasAtras = 7, total = 50) // fora da janela
        buscas(c.a, c.y, diasAtras = 1, total = 5)
        buscas(c.b, c.x, diasAtras = 0, total = 99) // outra UBS
        buscas(null, c.x, diasAtras = 0, total = 99) // sem UBS de referência: só na rede

        pedir("demanda-reprimida", ubsId = c.a).andExpect {
            // X e W estão na lista de desejos da A, mas ninguém os procurou lá nesses dias
            jsonPath("$.maisProcurados[*].medicamento.id") { value(contains(c.z.toInt(), c.y.toInt())) }
            jsonPath("$.maisProcurados[0].buscas") { value(7) }
            jsonPath("$.maisProcurados[1].buscas") { value(5) }
        }
    }

    @Test
    fun `lotes vencendo trazem os vencidos e os do prazo, do que vence primeiro, sem saldo zero`() {
        val c = cenario()

        pedir("lotes-vencendo", ubsId = c.a).andExpect {
            status { isOk() }
            jsonPath("$[*].numeroLote") { value(contains("VENCIDO", "EM30")) }
            jsonPath("$[0].diasParaVencer") { value(-5) }
            jsonPath("$[1].diasParaVencer") { value(30) }
            jsonPath("$[1].validade") { value(hoje.plusDays(30).toString()) }
            jsonPath("$[1].quantidade") { value(5) }
            jsonPath("$[1].medicamento.id") { value(c.z) }
            jsonPath("$[1].ubs.id") { value(c.a) }
        }
        pedir("lotes-vencendo", ubsId = c.a, parametros = arrayOf("dias" to "100")).andExpect {
            jsonPath("$[*].numeroLote") { value(contains("VENCIDO", "EM30", "EM90")) }
        }
        pedir("lotes-vencendo", ubsId = c.a, parametros = arrayOf("dias" to "1")).andExpect {
            jsonPath("$[*].numeroLote") { value(contains("VENCIDO")) }
        }
    }

    @Test
    fun `sincronizacao atrasada conta dias de calendario e a posicao de ontem ainda vale`() {
        val c = cenario() // A: posição de 2 dias atrás · B: de ontem · C: de hoje (sempre à meia-noite)
        ubs("9990009", "UBS Nunca Sincronizou", -9.65, -35.75, ultima = "NULL")

        pedir("sincronizacao-atrasada", ubsId = c.a).andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].ubs.cnes") { value("9990001") }
            jsonPath("$[0].atrasoDias") { value(2) }
        }
        // a posição de ontem (B) está a mais de 24 h, mas ainda vale o dia todo de hoje: não é atraso
        pedir("sincronizacao-atrasada", ubsId = c.b).andExpect { jsonPath("$.length()") { value(0) } }
        pedir("sincronizacao-atrasada", ubsId = c.c).andExpect { jsonPath("$.length()") { value(0) } }
        // com 2 dias de tolerância, a posição de 2 dias atrás (A) também vale
        pedir("sincronizacao-atrasada", ubsId = c.a, parametros = arrayOf("dias" to "2")).andExpect {
            jsonPath("$.length()") { value(0) }
        }
        val rede = pedir("sincronizacao-atrasada").andExpect {
            status { isOk() }
            jsonPath("$[*].ubs.cnes") { value(hasItem("9990009")) }
            jsonPath("$[*].ubs.cnes") { value(hasItem("9990001")) }
            jsonPath("$[*].ubs.cnes") { value(org.hamcrest.Matchers.not(hasItem("9990002"))) } // B: posição de ontem
            jsonPath("$[*].ubs.cnes") { value(org.hamcrest.Matchers.not(hasItem("9990003"))) } // C: posição de hoje
        }.andReturn().response.contentAsString
        val raiz = com.fasterxml.jackson.databind.ObjectMapper().readTree(rede)
        val cnes = raiz.map { it["ubs"]["cnes"].asText() }
        assertTrue(cnes.indexOf("9990009") < cnes.indexOf("9990001"), "quem nunca sincronizou vem primeiro: $cnes")
        assertTrue(raiz.first { it["ubs"]["cnes"].asText() == "9990009" }["atrasoDias"].isNull)
    }

    @Test
    fun `indicador de sincronizacao atrasada usa o mesmo criterio de dias de calendario`() {
        val c = cenario()

        pedir("indicadores", ubsId = c.a).andExpect { jsonPath("$.ubsComSincronizacaoAtrasada") { value(1) } }
        pedir("indicadores", ubsId = c.b).andExpect { jsonPath("$.ubsComSincronizacaoAtrasada") { value(0) } }
        pedir("indicadores", ubsId = c.c).andExpect { jsonPath("$.ubsComSincronizacaoAtrasada") { value(0) } }
    }

    @Test
    fun `gestor com vinculo recebe so a UBS dele e 403 ao pedir outra`() {
        val c = cenario()
        val propria = id("SELECT id FROM ubs WHERE cnes = '9000101'")

        for (rota in listOf("indicadores", "itens-atencao", "remanejamento", "demanda-reprimida", "lotes-vencendo", "sincronizacao-atrasada")) {
            pedir(rota, email = "carlos@medflow.local", ubsId = c.a).andExpect { status { isForbidden() } }
            pedir(rota, email = "carlos@medflow.local", ubsId = propria).andExpect { status { isOk() } }
        }
        pedir("indicadores", email = "carlos@medflow.local").andExpect {
            jsonPath("$.restrito") { value(true) }
            jsonPath("$.ubs.cnes") { value("9000101") }
        }
    }

    @Test
    fun `cidadao recebe 403, sem token 401 e parametros invalidos 400`() {
        for (rota in listOf("indicadores", "itens-atencao", "remanejamento", "demanda-reprimida", "lotes-vencendo", "sincronizacao-atrasada")) {
            pedir(rota, email = "rita@medflow.local").andExpect { status { isForbidden() } }
            pedir(rota, email = null).andExpect { status { isUnauthorized() } }
            pedir(rota, ubsId = 999999).andExpect { status { isNotFound() } }
            pedir(rota, parametros = arrayOf("ubsId" to "0")).andExpect { status { isBadRequest() } }
        }
        pedir("remanejamento", parametros = arrayOf("limite" to "0")).andExpect { status { isBadRequest() } }
        pedir("itens-atencao", parametros = arrayOf("limite" to "201")).andExpect { status { isBadRequest() } }
        pedir("demanda-reprimida", parametros = arrayOf("limite" to "101")).andExpect { status { isBadRequest() } }
        pedir("lotes-vencendo", parametros = arrayOf("dias" to "0")).andExpect { status { isBadRequest() } }
        pedir("lotes-vencendo", parametros = arrayOf("limite" to "201")).andExpect { status { isBadRequest() } }
        pedir("sincronizacao-atrasada", parametros = arrayOf("dias" to "31")).andExpect { status { isBadRequest() } }
        pedir("sincronizacao-atrasada", parametros = arrayOf("dias" to "0")).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `indicadores aplicam as regras de em falta, remessa a caminho, atrasada e demanda`() {
        val e = cenarioAtencao()

        pedir("indicadores", ubsId = e).andExpect {
            status { isOk() }
            jsonPath("$.itensEmFalta") { value(5) } // P, Q, T, U e V têm saldo zero (U com status DISPONIVEL também)
            jsonPath("$.emFaltaComRemessa") { value(2) } // P (a caminho) e V (recebida); a de T já está disponível
            jsonPath("$.abaixoDoMinimo") { value(2) } // R (7) e X (5); S (11) está acima de 10
            jsonPath("$.remessasACaminho") { value(2) } // P e X; V já foi recebida e T está disponível
            jsonPath("$.remessasAtrasadas") { value(1) } // X, com previsão de 2 dias atrás
            jsonPath("$.proximaRemessa") { value(hoje.plusDays(3).toString()) } // a atrasada não conta como próxima
            jsonPath("$.pacientesAguardando") { value(3) } // Duda e Edu (Q) e Fabi (U: status DISPONIVEL, mas sem saldo)
            jsonPath("$.medicamentosAguardados") { value(2) } // Q e U
        }
    }

    @Test
    fun `itens de atencao trazem situacao, aguardando e remessa prevista na ordem de prioridade`() {
        val e = cenarioAtencao()

        pedir("itens-atencao", ubsId = e).andExpect {
            status { isOk() }
            // em falta (mais pacientes primeiro), abaixo do mínimo e, por fim, com pedido enviado; S (11) fica de fora
            jsonPath("$[*].medicamento.nome") {
                value(contains("Med Q 10 mg", "Med U 10 mg", "Med T 10 mg", "Med V 10 mg", "Med R 10 mg", "Med P 10 mg", "Med X 10 mg"))
            }
            jsonPath("$[*].situacao") {
                value(contains("EM_FALTA", "EM_FALTA", "EM_FALTA", "EM_FALTA", "ABAIXO_MINIMO", "PEDIDO_ENVIADO", "PEDIDO_ENVIADO"))
            }
            jsonPath("$[0].aguardandoAviso") { value(2) }
            jsonPath("$[1].aguardandoAviso") { value(1) }
            jsonPath("$[0].estoque") { value(0) }
            jsonPath("$[0].minimo") { value(10) }
            jsonPath("$[0].remessaPrevista") { value(nullValue()) }
            jsonPath("$[3].remessaPrevista") { value(nullValue()) } // V: a remessa já foi recebida, não está a caminho
            jsonPath("$[4].estoque") { value(7) }
            jsonPath("$[5].remessaPrevista") { value(hoje.plusDays(3).toString()) }
            jsonPath("$[6].remessaPrevista") { value(hoje.minusDays(2).toString()) }
            jsonPath("$[0].ubs.id") { value(e) }
        }
        pedir("itens-atencao", ubsId = e, parametros = arrayOf("limite" to "2")).andExpect {
            jsonPath("$[*].medicamento.nome") { value(contains("Med Q 10 mg", "Med U 10 mg")) }
        }
    }

    @Test
    fun `gestor com vinculo ve so a propria UBS em todas as rotas, mesmo sem ubsId`() {
        val a = cenario() // dados da UBS A, que o Carlos não pode ver
        val propria = id("SELECT id FROM ubs WHERE cnes = '9000101'")
        val vizinha = id("SELECT id FROM ubs WHERE cnes = '9000102'")
        val m = medicamento("TESTE-C", "Gama C", 10)
        val itemDoCarlos = item(propria, m, 0)
        item(vizinha, m, 50) // sobra (50 > 2 x 10), na mesma cidade
        lote(itemDoCarlos, "CARLOS-VENCIDO", -3, 4)
        desejo(paciente("Gui"), propria, m)
        buscas(propria, m, diasAtras = 0, total = 2)
        buscas(a.a, a.x, diasAtras = 0, total = 5) // procurado na UBS A: fora do escopo do Carlos
        jdbc.update("UPDATE ubs SET ultima_sincronizacao = ${meiaNoite(3)} WHERE id = ?", propria)
        val carlos = "carlos@medflow.local"

        pedir("indicadores", email = carlos).andExpect { jsonPath("$.ubs.cnes") { value("9000101") } }
        pedir("itens-atencao", email = carlos).andExpect {
            jsonPath("$[*].ubs.id") { value(everyItem(equalTo(propria.toInt()))) }
            jsonPath("$[*].medicamento.id") { value(hasItem(m.toInt())) }
        }
        pedir("remanejamento", email = carlos).andExpect {
            jsonPath("$[*].destino.id") { value(everyItem(equalTo(propria.toInt()))) }
            jsonPath("$[*].medicamento.id") { value(hasItem(m.toInt())) }
            // a origem pode ser de outra UBS da cidade: é intencional, o gestor vê a sobra dos vizinhos
            jsonPath("$[?(@.medicamento.id == $m)].origem.id") { value(contains(vizinha.toInt())) }
        }
        pedir("demanda-reprimida", email = carlos).andExpect {
            jsonPath("$.itens[*].ubs.id") { value(everyItem(equalTo(propria.toInt()))) }
            jsonPath("$.itens[*].medicamento.id") { value(hasItem(m.toInt())) }
            jsonPath("$.maisProcurados[*].medicamento.id") { value(contains(m.toInt())) }
        }
        pedir("lotes-vencendo", email = carlos).andExpect {
            jsonPath("$[*].ubs.id") { value(everyItem(equalTo(propria.toInt()))) }
            jsonPath("$[*].numeroLote") { value(hasItem("CARLOS-VENCIDO")) }
        }
        pedir("sincronizacao-atrasada", email = carlos).andExpect {
            jsonPath("$[*].ubs.cnes") { value(contains("9000101")) }
        }

        // a gestora da rede (Marta) enxerga também os dados da UBS A, fora do escopo do Carlos
        pedir("lotes-vencendo", parametros = arrayOf("limite" to "200")).andExpect {
            jsonPath("$[*].numeroLote") { value(hasItem("VENCIDO")) }
        }
        pedir("itens-atencao", parametros = arrayOf("limite" to "200")).andExpect {
            jsonPath("$[*].ubs.id") { value(hasItem(a.a.toInt())) }
        }
        pedir("demanda-reprimida", parametros = arrayOf("limite" to "100")).andExpect {
            jsonPath("$.itens[*].ubs.id") { value(hasItem(a.a.toInt())) }
        }
        pedir("sincronizacao-atrasada").andExpect {
            jsonPath("$[*].ubs.cnes") { value(hasItem("9990001")) }
        }
    }
}
