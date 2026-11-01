package com.example.taskflow.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.example.taskflow.model.UserRecord
import java.util.Date

class JwtTokenService(
    secret: String,
    private val issuer: String = "taskflow-api",
    private val audience: String = "taskflow-android",
    private val validityMillis: Long = 30L * 24 * 60 * 60 * 1_000
) {
    private val algorithm = Algorithm.HMAC256(secret)

    fun issue(user: UserRecord): String {
        val issuedAt = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(user.id)
            .withClaim("email", user.email)
            .withIssuedAt(Date(issuedAt))
            .withExpiresAt(Date(issuedAt + validityMillis))
            .sign(algorithm)
    }

    fun verifier(): JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()
}
