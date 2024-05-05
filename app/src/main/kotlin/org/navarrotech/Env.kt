package org.navarrotech

import io.github.cdimascio.dotenv.Dotenv

object Env {
    private val dotenv: Dotenv = Dotenv.load()

    // Home directory:
    val homeDir = System.getProperty("user.home");

    // Production flag:
    val isProduction = get("ENV") == "production"

    @JvmStatic
    fun get(key: String): String? {
        return dotenv[key]
    }

    @JvmStatic
    fun get(key: String, defaultValue: String): String {
        return dotenv[key, defaultValue]
    }
}
