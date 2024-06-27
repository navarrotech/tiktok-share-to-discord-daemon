package org.navarrotech

import com.teamdev.jxbrowser.js.JsFunctionCallback
import com.teamdev.jxbrowser.js.JsObject
import org.apache.logging.log4j.LogManager

import java.time.Duration
import kotlinx.coroutines.*

import netscape.javascript.JSObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.postgresql.util.PSQLException

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketException
import java.nio.file.Files

class TikTokManager (private val viewEngine: ViewEngine) {

    private val logger = LogManager.getLogger(TikTokManager::class.java)

    private val bufferedReader: BufferedReader = File("src/main/resources/Scraper.js").bufferedReader()
    private val scraperScript = bufferedReader.use { it.readText() }

    private val username = Env.get("TIKTOK_USERNAME", "foo")
    private val password = Env.get("TIKTOK_PASSWORD", "foo")

    private val usernameToWebhookMap = mutableMapOf<String, String>()
    private val usernameToServerId = mutableMapOf<String, Int>()

    init {
        updateWebhookMap()
    }

    private fun updateWebhookMap() {
        Database.makeConnection().use {
            try {
                val statement = it.createStatement()
                val resultSet = statement.executeQuery("select * from servers;")
                usernameToWebhookMap.clear()
                while (resultSet.next()) {
                    usernameToWebhookMap[
                        resultSet.getString("tiktok_username")
                    ] = resultSet.getString("discord_webhook")
                    usernameToServerId[
                        resultSet.getString("tiktok_username")
                    ] = resultSet.getInt("id")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun executeJs(command: String) = withContext(Dispatchers.IO) {
        viewEngine.browser.mainFrame().ifPresent { frame ->
            frame.executeJavaScript<String>(command)
        }
    }

    // Separate coroutine
    private fun onConversationReport(fromUsername: String, videoUrl: String, videoSourceUrl: String, authorUsername: String, description: String) = CoroutineScope(Dispatchers.IO).launch {
        logger.info("New request by $fromUsername for the video: $videoUrl")

        val webhookUrl = usernameToWebhookMap[fromUsername] ?: return@launch
        val serverId = usernameToServerId[fromUsername] ?: return@launch

        logger.info(" >> Webhook URL found")

        Database.makeConnection().use {
            try {
                val statement = it.createStatement()
                val videoId = "$serverId-$videoUrl"
                val insertStatement = it.prepareStatement(
                    "INSERT INTO history (id, server_id, tiktok_username, tiktok_url, video_source_url, author_username, description, status, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, 'downloading', CURRENT_TIMESTAMP)"
                )
                insertStatement.setString(1, videoId)
                insertStatement.setInt(2, serverId)
                insertStatement.setString(3, fromUsername)
                insertStatement.setString(4, videoUrl)
                insertStatement.setString(5, videoSourceUrl)
                insertStatement.setString(6, authorUsername)
                insertStatement.setString(7, description)
                insertStatement.executeUpdate()

                logger.info(" >> History added")

                val client = OkHttpClient()

                // Download the video to a temporary file
                val downloadRequest = Request.Builder().url(videoSourceUrl).build()
                val tempFile = Files.createTempFile("downloaded_video", ".mp4").toFile()

                try {
                    val response = client.newCall(downloadRequest).execute()
                    if (!response.isSuccessful){
                        throw IOException("Unexpected code $response")
                    }

                    // Create a temporary file
                    tempFile.deleteOnExit()
                    FileOutputStream(tempFile).use { output ->
                        response.body?.byteStream()?.copyTo(output)
                    }
                    logger.info(" >> Download successful, temporary file located at: ${tempFile.absolutePath}")
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Upload the video to the Discord webhook
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        "$authorUsername.mp4",
                        tempFile.asRequestBody("application/octet-stream".toMediaType())
                    )
                    .addFormDataPart("content", description)
                    .build()

                val uploadRequest = Request.Builder()
                    .url(webhookUrl)
                    .post(requestBody)
                    .build()

                client.newCall(uploadRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code $response")
                    }
                    println("File uploaded: ${response.body?.string()}")
                }

                logger.info(" >> File uploaded to Discord")

                // Update the status in the database
                statement.execute(
                    "UPDATE history SET status='completed' WHERE id='${videoId}'"
                )

                client.dispatcher.executorService.shutdown()  // Shutdown OkHttp threads
                tempFile.delete()

                logger.info(" >> Status updated, temp file deleted. Finished")

            }
            catch (e: PSQLException){
                if (e.message?.contains("duplicate key value violates unique constraint") != true) {
                    e.printStackTrace()
                }
            }
            catch (e: SocketException){
                e.printStackTrace()
            }
            catch (e: IOException){
                e.printStackTrace()
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun takeControl() = CoroutineScope(Dispatchers.IO).launch {
        viewEngine.browser.navigation().loadUrlAndWait(
            "https://www.tiktok.com/messages?lang=en",
            Duration.ofSeconds(45)
        )

        if (!isLoggedIn()) {
            login()
        }

        viewEngine.waitForDOMElementToExist("[data-e2e=message-title]")

        injectJs()
        println("Initialization complete")
    }

    private suspend fun injectJs() = coroutineScope {
        viewEngine.browser.mainFrame().ifPresent { frame ->
            val window = frame.executeJavaScript<JsObject>("window")
            window?.putProperty("reportConversation", JsFunctionCallback { args ->
                logger.info("Received a report callback")

                // First arg should be username
                // Second arg should be the video url
                // Third arg should be the video source url
                // Fourth arg should be the author's username
                // Fifth arg should be the description

                onConversationReport(
                    args[0].toString(),
                    args[1].toString(),
                    args[2].toString(),
                    args[3].toString(),
                    args[4].toString()
                )
            })

            frame.executeJavaScript<String>(scraperScript.trimIndent())
        }
    }

    private fun isLoggedIn(): Boolean {
        val url: String = viewEngine.browser.url()
        return !url.contains("www.tiktok.com/login")
    }

    private suspend fun login() = coroutineScope {
        delay(500)

        // Click the "login" button
        executeJs("""
            document.querySelectorAll("[data-e2e=channel-item]")[1]?.click()
        """.trimIndent())

        logger.debug("Clicked the login button")

        delay(100)

        executeJs("""
            document.querySelector("a[href='/login/phone-or-email/email']")?.click()
        """.trimIndent())

        logger.debug("Clicked the email login button")

        delay(1000)

        executeJs("""
            const username = document.querySelector("input[name='username']")
            console.log(username)
            if (username) {
                // Trigger change:
                const nativeInputValueSetter = Object.getOwnPropertyDescriptor(
                  window.HTMLInputElement.prototype,
                  'value').set;
                nativeInputValueSetter.call(username, "$username");
                const usernameInputEvent = new Event('input', { bubbles: true })
                username.dispatchEvent(usernameInputEvent)
            }
        """.trimIndent())

        logger.debug("Entered the username")

        delay(200)

        executeJs("""
            const password = document.querySelector("input[type='password']")
            console.log(password)
            if (password) {
                // Trigger change:
                const nativeInputValueSetter = Object.getOwnPropertyDescriptor(
                  window.HTMLInputElement.prototype,
                  'value').set;
                nativeInputValueSetter.call(password, "$password");
                const passwordInputEvent = new Event('input', { bubbles: true })
                password.dispatchEvent(passwordInputEvent)
            }
        """.trimIndent())

        logger.debug("Entered the password")

        delay(200)

        executeJs("""
            const submitButton = document.querySelector("button[type='submit']")
            console.log(submitButton)
            if (submitButton) {
                submitButton.click()
            }
        """.trimIndent())

        logger.debug("Clicked the submit button")

        viewEngine.browser.mainFrame().ifPresent { frame ->
            val submitBtn = frame.executeJavaScript<JSObject>("document.querySelector('button[type=submit]')")
            // if it's disabled, log it
            if (submitBtn?.getMember("disabled") == true) {
                logger.error("Submit button is disabled")
            }
        }

        delay(10_000)
    }
}