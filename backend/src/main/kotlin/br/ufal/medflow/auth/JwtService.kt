package br.ufal.medflow.auth

import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Clock

data class TokenEmitido(val token: String, val expiraEmSegundos: Long)

/** Emite o JWT (HS256). O `sub` é o id do usuário; `papel` e `email` vão como claims. */
@Service
class JwtService(
    private val encoder: JwtEncoder,
    private val props: JwtProperties,
    private val relogio: Clock,
) {

    fun emitir(usuario: Usuario): TokenEmitido {
        val agora = relogio.instant()
        val claims = JwtClaimsSet.builder()
            .issuer(props.emissor)
            .issuedAt(agora)
            .expiresAt(agora.plus(props.expiracao))
            .subject(requireNotNull(usuario.id).toString())
            .claim("email", usuario.email)
            .claim("papel", usuario.papel.name)
            .build()
        val cabecalho = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).tokenValue
        return TokenEmitido(token, props.expiracao.seconds)
    }
}
