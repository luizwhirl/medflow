package br.ufal.medflow.auth

import com.nimbusds.jose.jwk.source.ImmutableSecret
import jakarta.servlet.DispatcherType
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher
import java.time.Clock
import javax.crypto.spec.SecretKeySpec

/**
 * API sem sessão: cada requisição leva um JWT (HS256).
 * Leitura de UBS e medicamentos é pública.
 * Lista de desejos e perfil são exclusivos do papel CIDADAO.
 * O prefixo /api/gestao é exclusivo do papel GESTOR.
 * O restante exige login.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig(private val props: JwtProperties) {

    private val chave = SecretKeySpec(props.secret.toByteArray(), "HmacSHA256")

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            cors { } // usa o CORS do WebConfig
            csrf { disable() } // sem sessão nem cookie, não há o que forjar
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                // Erros tratados pelo Spring são encaminhados para /error.
                authorize(DispatcherTypeRequestMatcher(DispatcherType.ERROR), permitAll)
                authorize(HttpMethod.OPTIONS, "/**", permitAll)
                authorize(HttpMethod.POST, "/api/auth/login", permitAll)
                authorize(HttpMethod.POST, "/api/auth/cadastro", permitAll)
                authorize(HttpMethod.GET, "/api/ubs/**", permitAll)
                authorize(HttpMethod.GET, "/api/medicamentos/**", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize("/swagger-ui.html", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/v3/api-docs/**", permitAll)

                // Funcionalidades exclusivas dos cidadãos.
                authorize("/api/lista-desejos", hasRole("CIDADAO"))
                authorize("/api/lista-desejos/**", hasRole("CIDADAO"))
                authorize("/api/me", hasRole("CIDADAO"))
                authorize("/api/me/**", hasRole("CIDADAO"))

                // Funcionalidades exclusivas dos gestores.
                authorize("/api/gestao/**", hasRole("GESTOR"))

                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                jwt { jwtAuthenticationConverter = conversorDePapel() }
                authenticationEntryPoint = RespostasDeSeguranca
                accessDeniedHandler = RespostasDeSeguranca
            }
            exceptionHandling {
                authenticationEntryPoint = RespostasDeSeguranca
                accessDeniedHandler = RespostasDeSeguranca
            }
        }
        return http.build()
    }

    /** A claim papel vira a autoridade ROLE_CIDADAO ou ROLE_GESTOR. */
    private fun conversorDePapel() = JwtAuthenticationConverter().apply {
        setJwtGrantedAuthoritiesConverter(
            JwtGrantedAuthoritiesConverter().apply {
                setAuthoritiesClaimName("papel")
                setAuthorityPrefix("ROLE_")
            },
        )
    }

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build().apply {
            setJwtValidator(JwtValidators.createDefaultWithIssuer(props.emissor))
        }

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(chave))

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun relogio(): Clock = Clock.systemUTC()
}
