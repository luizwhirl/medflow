package br.ufal.medflow.medicamento

import br.ufal.medflow.TestcontainersConfiguration
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class BuscaToleranteIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @BeforeEach
    fun prepararMedicamentos() {
        inserir("VOZ-001", "Losartana potássica", "Hipertensão")
        inserir("VOZ-002", "Metformina", "Diabetes")
        inserir("VOZ-003", "Dipirona sódica", "Dor e febre")
    }

    private fun inserir(catmat: String, nome: String, categoria: String) {
        jdbc.update(
            """
            INSERT INTO medicamento
                (catmat, nome, concentracao, forma_farmaceutica, categoria)
            VALUES (?, ?, '50 mg', 'comprimido', ?)
            """,
            catmat,
            nome,
            categoria,
        )
    }

    @Test
    fun `encontra medicamento em frase com dose e unidade`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "losartana 50 miligramas")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens[0].nome") { value("Losartana potássica") }
        }
    }

    @Test
    fun `ignora palavras irrelevantes em frase de voz`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "remédio de pressão losartana")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens[0].nome") { value("Losartana potássica") }
        }
    }


    @Test
    fun `encontra medicamento em transcricao com virgulas`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "remédio, de pressão, losartana, 50 mg")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens[0].nome") { value("Losartana potássica") }
        }
    }

    @Test
    fun `ignora pontuacao e caixa`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "METFORMINA.")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens[0].nome") { value("Metformina") }
        }
    }

    @Test
    fun `encontra nome cadastrado com acento usando termo sem acento`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "dipirona sodica")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens[0].nome") { value("Dipirona sódica") }
        }
    }

    @Test
    fun `tolera uma letra adicional no nome`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "metiformina")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens[0].nome") { value("Metformina") }
        }
    }

    @Test
    fun `tolera uma letra ausente no nome`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "losartan")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens[0].nome") { value("Losartana potássica") }
        }
    }

    @Test
    fun `preserva busca parcial tradicional`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "met")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens.length()") { value(1) }
            jsonPath("$.itens[0].nome") { value("Metformina") }
        }
    }

    @Test
    fun `preserva busca textual por categoria`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "hipertensao")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens[0].nome") { value("Losartana potássica") }
        }
    }

    @Test
    fun `aceita alternativas na mesma requisicao`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "losartana", "metformina")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalItens") { value(2) }
            jsonPath("$.itens[*].nome") { value(hasItem("Losartana potássica")) }
            jsonPath("$.itens[*].nome") { value(hasItem("Metformina")) }
        }
    }

    @Test
    fun `nao duplica medicamento encontrado por duas alternativas`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "losartana", "losartan")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalItens") { value(1) }
            jsonPath("$.itens.length()") { value(1) }
        }
    }

    @Test
    fun `prioriza correspondencia de mais palavras e prefixo do nome`() {
        inserir("VOZ-004", "Potássica Losartana", "Hipertensão")
        inserir("VOZ-005", "Losartana", "Hipertensão")

        mockMvc.get("/api/medicamentos") {
            param("busca", "losartana potassica")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens[0].nome") { value("Losartana potássica") }
            jsonPath("$.itens[1].nome") { value("Potássica Losartana") }
            jsonPath("$.itens[2].nome") { value("Losartana") }
        }
    }

    @Test
    fun `combina alternativas com filtro por categoria normalizado`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "losartana", "metformina")
            param("categoria", "HIPERTENSAO")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalItens") { value(1) }
            jsonPath("$.itens[0].nome") { value("Losartana potássica") }
        }
    }

    @Test
    fun `filtro isolado por categoria ignora acentos`() {
        mockMvc.get("/api/medicamentos") {
            param("categoria", "hipertensao")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalItens") { value(1) }
            jsonPath("$.itens[0].nome") { value("Losartana potássica") }
        }
    }

    @Test
    fun `mantem paginacao e totais para multiplas alternativas`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "losartana", "metformina")
            param("pagina", "1")
            param("tamanho", "1")
        }.andExpect {
            status { isOk() }
            jsonPath("$.itens.length()") { value(1) }
            jsonPath("$.pagina") { value(1) }
            jsonPath("$.tamanho") { value(1) }
            jsonPath("$.totalItens") { value(2) }
            jsonPath("$.totalPaginas") { value(2) }
        }
    }

    @Test
    fun `retorna pagina vazia quando nao existem palavras uteis`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "remédio de 50 mg")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalItens") { value(0) }
            jsonPath("$.itens.length()") { value(0) }
        }
    }

    @Test
    fun `retorna pagina vazia para termo sem correspondencia`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "zzzzzzzzzz")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalItens") { value(0) }
        }
    }

    @Test
    fun `pontuacao nao funciona como curinga SQL`() {
        mockMvc.get("/api/medicamentos") {
            param("busca", "%_")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalItens") { value(0) }
        }
    }

    @Test
    fun `atualizacao do cadastro atualiza a busca normalizada`() {
        jdbc.update(
            """
            UPDATE medicamento
            SET nome = 'Ácido acetilsalicílico', categoria = 'Analgésicos'
            WHERE catmat = 'VOZ-003'
            """,
        )

        mockMvc.get("/api/medicamentos") {
            param("busca", "acido acetilsalicilico")
            param("categoria", "analgesicos")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalItens") { value(1) }
            jsonPath("$.itens[0].nome") { value("Ácido acetilsalicílico") }
        }
    }
}

/** Medição da busca com os dados de desenvolvimento do projeto. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev", "demo")
@Import(TestcontainersConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class BuscaToleranteSeedIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `mede tempo de resposta da busca tolerante com seed`() {
        fun buscar() {
            mockMvc.get("/api/medicamentos") {
                param("busca", "remédio de 850 miligramas metiformina", "losartan")
                param("tamanho", "20")
            }.andExpect {
                status { isOk() }
                jsonPath("$.itens[0].nome") { exists() }
            }
        }

        // Aquecimento da aplicação e do banco.
        repeat(3) { buscar() }

        val tempos = List(10) {
            val inicio = System.nanoTime()
            buscar()
            (System.nanoTime() - inicio) / 1_000_000.0
        }.sorted()

        val mediana = (tempos[4] + tempos[5]) / 2
        val maximo = tempos.last()

        println("PDS-51 - Tempos em ms: $tempos")
        println("PDS-51 - Mediana: $mediana ms; máximo: $maximo ms")

        assertTrue(
            mediana < 300.0,
            "Mediana da busca com seed acima de 300 ms: $mediana",
        )
    }
}
