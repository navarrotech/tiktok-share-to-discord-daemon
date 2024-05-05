package org.navarrotech

import java.sql.Connection
import java.sql.DriverManager
//import org.postgresql.util.PSQLException

class Database {

    companion object {
        private val url = Env.get("DATABASE_URL")
        private val user = Env.get("DATABASE_USERNAME")
        private val password = Env.get("DATABASE_PASSWORD")

        fun makeConnection(): Connection {
            return DriverManager.getConnection(url, user, password)
        }

        fun migrate(){
            val connection = makeConnection()
            connection.use {
                val statement = it.createStatement()
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS servers (" +
                            "id INT PRIMARY KEY," +
                            "tiktok_username TEXT," +
                            "discord_webhook TEXT" +
                            ")"
                )

                statement.execute(
                    "CREATE TABLE IF NOT EXISTS history (" +
                            "id TEXT PRIMARY KEY UNIQUE," +
                            "server_id INT," +
                            "tiktok_username TEXT," +
                            "tiktok_url TEXT," +
                            "video_source_url TEXT," +
                            "author_username TEXT," +
                            "description TEXT," +
                            "status TEXT," +
                            "timestamp TIMESTAMP" +
                            ")"
                )


                try {
                    statement.execute(
                        "ALTER TABLE history ADD CONSTRAINT fk_server_id FOREIGN KEY (server_id) REFERENCES servers(id)"
                    )
                }
//                catch (e: PSQLException) {
//                    // TODO
//                }
                catch (e: Exception) {
//                    e.printStackTrace()
                }
            }
            println("Database migrated")
        }
    }
}