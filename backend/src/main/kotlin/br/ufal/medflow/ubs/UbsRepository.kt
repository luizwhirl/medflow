package br.ufal.medflow.ubs

import org.springframework.data.jpa.repository.JpaRepository

interface UbsRepository : JpaRepository<Ubs, Long> {
    fun findAllByOrderByNomeAsc(): List<Ubs>
}
