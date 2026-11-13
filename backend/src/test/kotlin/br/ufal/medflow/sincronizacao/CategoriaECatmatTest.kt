package br.ufal.medflow.sincronizacao

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CategoriaECatmatTest {

    @Test
    fun `categoria vem do principio ativo`() {
        assertEquals("Diabetes", CategoriaMedicamento.de("Metformina, cloridrato"))
        assertEquals("Diabetes", CategoriaMedicamento.de("Insulina humana NPH"))
        assertEquals("Hipertensão", CategoriaMedicamento.de("Losartana potássica"))
        assertEquals("Hipertensão", CategoriaMedicamento.de("Enalapril, maleato"))
        assertEquals("Colesterol", CategoriaMedicamento.de("Sinvastatina"))
        assertEquals("Antibióticos", CategoriaMedicamento.de("Amoxicilina"))
        assertEquals("Asma", CategoriaMedicamento.de("Salbutamol, sulfato"))
        assertEquals("Dor e febre", CategoriaMedicamento.de("Dipirona sódica"))
        assertEquals("Estômago", CategoriaMedicamento.de("Omeprazol"))
    }

    @Test
    fun `sem principio ativo conhecido fica em outros`() {
        assertEquals("Outros", CategoriaMedicamento.de("Clonazepam"))
    }

    @Test
    fun `catmat perde o sufixo da unidade de fornecimento`() {
        assertEquals("BR0267691", Catmat.base("BR0267691U0042"))
        assertEquals("BR0267691", Catmat.base("br0267691u0045"))
        assertEquals("BR0266863", Catmat.base("BR0266863U0066INAT"))
        assertEquals("BR0272365", Catmat.base("BR0272365"))
        assertEquals("ABC", Catmat.base(" abc "))
    }
}
