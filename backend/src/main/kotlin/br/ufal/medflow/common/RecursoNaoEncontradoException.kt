package br.ufal.medflow.common

class RecursoNaoEncontradoException(recurso: String, id: Any) :
    RuntimeException("$recurso $id não encontrado(a)")
