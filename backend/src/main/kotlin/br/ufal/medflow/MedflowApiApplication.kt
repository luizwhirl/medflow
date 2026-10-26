package br.ufal.medflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MedflowApiApplication

fun main(args: Array<String>) {
	runApplication<MedflowApiApplication>(*args)
}
