package com.example.taskflow.auth

import org.mindrot.jbcrypt.BCrypt

interface PasswordHasher {
    fun hash(password: String): String
    fun verify(password: String, passwordHash: String): Boolean
}

class BcryptPasswordHasher(
    private val logRounds: Int = 12
) : PasswordHasher {
    override fun hash(password: String): String =
        BCrypt.hashpw(password, BCrypt.gensalt(logRounds))

    override fun verify(password: String, passwordHash: String): Boolean =
        runCatching { BCrypt.checkpw(password, passwordHash) }.getOrDefault(false)
}
