package br.ufal.medflow.auth

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @Operation(summary = "Entra com e-mail e senha e recebe o token JWT (usar como `Authorization: Bearer <token>`)")
    @PostMapping("/login")
    fun login(@Valid @RequestBody pedido: LoginRequest): TokenResponse = authService.login(pedido.email, pedido.senha)

    @Operation(summary = "Cria uma conta de cidadão. O papel não pode ser escolhido")
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    fun cadastrar(@Valid @RequestBody pedido: CadastroRequest): UsuarioResponse = authService.cadastrar(pedido)

    @Operation(summary = "Dados da conta do token")
    @GetMapping("/eu")
    fun eu(@AuthenticationPrincipal jwt: Jwt): UsuarioResponse = authService.conta(jwt.subject ?: throw CredenciaisInvalidasException())
}
