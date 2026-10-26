package br.ufal.medflow

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<MedflowApiApplication>().with(TestcontainersConfiguration::class).run(*args)
}
