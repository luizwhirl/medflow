package br.ufal.medflow.medicamento

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Normaliza as alternativas, separa palavras úteis e encontra correspondências
 * por nome, categoria ou similaridade.
 *
 * Cada alternativa é avaliada separadamente, evitando somar artificialmente
 * a relevância de transcrições diferentes do mesmo reconhecimento de voz.
 */
private const val CTE_BUSCA = """
    WITH alternativas AS (
        SELECT DISTINCT
            trim(regexp_replace(
                lower(unaccent(a.texto)),
                '[^[:alnum:]]+', ' ', 'g'
            )) AS frase
        FROM unnest(string_to_array(:buscas, chr(10))) AS a(texto)
    ),
    palavras AS (
        SELECT DISTINCT a.frase, p.palavra
        FROM alternativas a
        CROSS JOIN LATERAL regexp_split_to_table(a.frase, ' +') AS p(palavra)
        WHERE p.palavra ~ '[[:alpha:]]'
          AND p.palavra NOT IN (
              'remedio', 'remedios', 'medicamento', 'medicamentos',
              'de', 'da', 'do', 'das', 'dos',
              'o', 'a', 'os', 'as', 'um', 'uma',
              'e', 'para', 'por', 'com', 'em',
              'mg', 'g', 'mcg', 'ug', 'ml', 'ui',
              'miligrama', 'miligramas',
              'grama', 'gramas',
              'micrograma', 'microgramas',
              'mililitro', 'mililitros',
              'unidade', 'unidades',
              'comprimido', 'comprimidos',
              'capsula', 'capsulas'
          )
          AND p.palavra !~ '^[0-9]+(mg|g|mcg|ug|ml|ui)$'
    ),
    correspondencias AS (
        SELECT
            m.id,
            p.frase,
            p.palavra,
            CASE
                WHEN m.nome_busca LIKE p.frase || '%' THEN 1
                ELSE 0
            END AS prefixo_frase,
            CASE
                WHEN m.nome_busca LIKE p.palavra || '%' THEN 1
                ELSE 0
            END AS prefixo_palavra,
            CASE
                WHEN m.nome_busca LIKE '%' || p.palavra || '%' THEN 1
                ELSE 0
            END AS acerto_nome,
            CASE
                WHEN m.nome_busca LIKE '%' || p.palavra || '%'
                  OR m.categoria_busca LIKE '%' || p.palavra || '%'
                THEN 1
                ELSE 0
            END AS acerto_textual,
            CASE
                WHEN m.nome_busca LIKE '%' || p.palavra || '%'
                  OR m.categoria_busca LIKE '%' || p.palavra || '%'
                THEN 0.0
                ELSE word_similarity(p.palavra, m.nome_busca)
            END AS similaridade
        FROM palavras p
        JOIN medicamento m ON (
            m.nome_busca LIKE '%' || p.palavra || '%'
            OR m.categoria_busca LIKE '%' || p.palavra || '%'
            OR (
                char_length(p.palavra) >= 5
                AND m.nome_busca %> p.palavra
            )
        )
        WHERE (
            :categoria = ''
            OR m.categoria_busca = lower(unaccent(:categoria))
        )
    )
"""

interface MedicamentoRepository : JpaRepository<Medicamento, Long> {

    /**
     * Busca paginada por relevância.
     *
     * A melhor alternativa determina a classificação de cada medicamento.
     * Nome e id garantem desempate estável entre páginas.
     */
    @Query(
        value = CTE_BUSCA + """
            , pontuacoes AS (
                SELECT
                    id,
                    frase,
                    count(*) AS acertos,
                    max(prefixo_frase) AS prefixo_frase,
                    sum(prefixo_palavra) AS prefixos,
                    sum(acerto_nome) AS acertos_nome,
                    sum(acerto_textual) AS acertos_textuais,
                    sum(similaridade) AS similaridade
                FROM correspondencias
                GROUP BY id, frase
            ),
            melhores AS (
                SELECT DISTINCT ON (id)
                    id,
                    acertos,
                    prefixo_frase,
                    prefixos,
                    acertos_nome,
                    acertos_textuais,
                    similaridade
                FROM pontuacoes
                ORDER BY
                    id,
                    acertos DESC,
                    prefixo_frase DESC,
                    prefixos DESC,
                    acertos_nome DESC,
                    acertos_textuais DESC,
                    similaridade DESC
            )
            SELECT m.*
            FROM medicamento m
            JOIN melhores b ON b.id = m.id
            ORDER BY
                b.acertos DESC,
                b.prefixo_frase DESC,
                b.prefixos DESC,
                b.acertos_nome DESC,
                b.acertos_textuais DESC,
                b.similaridade DESC,
                m.nome,
                m.id
        """,
        countQuery = CTE_BUSCA + """
            SELECT count(DISTINCT id)
            FROM correspondencias
        """,
        nativeQuery = true,
    )
    fun buscarPorRelevancia(
        @Param("buscas") buscas: String,
        @Param("categoria") categoria: String,
        pagina: Pageable,
    ): Page<Medicamento>

    @Query(
        value = """
            SELECT m.*
            FROM medicamento m
            WHERE m.categoria_busca = lower(unaccent(:categoria))
            ORDER BY m.nome, m.id
        """,
        countQuery = """
            SELECT count(*)
            FROM medicamento m
            WHERE m.categoria_busca = lower(unaccent(:categoria))
        """,
        nativeQuery = true,
    )
    fun buscarPorCategoria(
        @Param("categoria") categoria: String,
        pagina: Pageable,
    ): Page<Medicamento>

    @Query(
        """
        select distinct m.categoria from Medicamento m
        where m.categoria is not null
        order by m.categoria
        """,
    )
    fun listarCategorias(): List<String>

    fun findByCatmatIn(catmats: Collection<String>): List<Medicamento>
}
