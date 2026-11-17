package br.ufal.medflow.auth

/** E-mail ou senha errados. A mensagem é a mesma nos dois casos, para não revelar quais e-mails têm conta. */
class CredenciaisInvalidasException : RuntimeException("E-mail ou senha inválidos")

class EmailJaCadastradoException : RuntimeException("Já existe uma conta com esse e-mail")
