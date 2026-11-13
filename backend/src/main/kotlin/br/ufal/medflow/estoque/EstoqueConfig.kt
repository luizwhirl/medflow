package br.ufal.medflow.estoque

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(EstoqueProperties::class)
class EstoqueConfig
