package br.ufal.medflow.sincronizacao

import java.text.Normalizer

/**
 * A BNAFAR não traz categoria terapêutica. Heurística do MVP: o princípio ativo no nome
 * define a condição usada na busca por condição. O que não casar fica em "Outros".
 */
object CategoriaMedicamento {

    const val OUTROS = "Outros"

    private val POR_PRINCIPIO_ATIVO = linkedMapOf(
        "Diabetes" to listOf("METFORMINA", "GLIBENCLAMIDA", "GLICLAZIDA", "INSULINA"),
        "Hipertensão" to listOf(
            "LOSARTANA", "HIDROCLOROTIAZIDA", "CAPTOPRIL", "ANLODIPINO", "ENALAPRIL", "ATENOLOL", "PROPRANOLOL",
            "CARVEDILOL", "NIFEDIPINO", "ESPIRONOLACTONA", "FUROSEMIDA",
        ),
        "Colesterol" to listOf("SINVASTATINA", "ATORVASTATINA", "ROSUVASTATINA"),
        "Antibióticos" to listOf(
            "AMOXICILINA", "AZITROMICINA", "CEFALEXINA", "BENZILPENICILINA", "CIPROFLOXACINO", "DOXICICLINA",
            "SULFAMETOXAZOL", "METRONIDAZOL",
        ),
        "Asma" to listOf("SALBUTAMOL", "BECLOMETASONA", "IPRATROPIO", "FENOTEROL"),
        "Dor e febre" to listOf("DIPIRONA", "PARACETAMOL", "IBUPROFENO", "DICLOFENACO"),
        "Estômago" to listOf("OMEPRAZOL", "RANITIDINA", "METOCLOPRAMIDA"),
    )

    fun de(nome: String): String {
        val normalizado = Normalizer.normalize(nome, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").uppercase()
        return POR_PRINCIPIO_ATIVO.entries.firstOrNull { (_, ativos) -> ativos.any { it in normalizado } }?.key ?: OUTROS
    }
}
