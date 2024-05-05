package org.navarrotech

import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.browser.callback.InjectJsCallback
import com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback
import com.teamdev.jxbrowser.browser.event.BrowserClosed
import com.teamdev.jxbrowser.browser.event.ConsoleMessageReceived
import com.teamdev.jxbrowser.dom.DomKeyCode
import com.teamdev.jxbrowser.dom.event.EventParams
import com.teamdev.jxbrowser.dom.event.KeyEventParams
import com.teamdev.jxbrowser.dom.event.UiEventModifierParams
import com.teamdev.jxbrowser.engine.Engine
import com.teamdev.jxbrowser.engine.EngineOptions
import com.teamdev.jxbrowser.engine.Language
import com.teamdev.jxbrowser.engine.RenderingMode
import com.teamdev.jxbrowser.engine.event.EngineCrashed
import com.teamdev.jxbrowser.js.ConsoleMessageLevel
import com.teamdev.jxbrowser.js.JsObject
import com.teamdev.jxbrowser.net.HttpHeader
import com.teamdev.jxbrowser.net.HttpStatus
import com.teamdev.jxbrowser.net.Scheme
import com.teamdev.jxbrowser.net.UrlRequestJob
import com.teamdev.jxbrowser.net.callback.InterceptUrlRequestCallback
import com.teamdev.jxbrowser.ui.KeyCode
import com.teamdev.jxbrowser.view.swing.BrowserView
import jdk.jfr.EventType
import netscape.javascript.JSObject

import java.io.File

import java.net.URLConnection

import org.apache.logging.log4j.LogManager
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit

import kotlin.io.path.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively

import kotlin.system.exitProcess

class ViewEngine {
    val width = 1280
    val height = 1280

    var engine: Engine
    var browser: Browser

    private val logger = LogManager.getLogger(ViewEngine::class.java)

    companion object {
        val licenseKey: String? = Env.get(
            "JXBROWSER_LICENSE_KEY"
        )
        // The user can optionally change their user agent through env. Might be useful to set a unique user agent in production.
        val userAgent = Env.get(
            "UserAgent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        )
    }

    init {
        // The recommended way to set the JxBrowser license key is to use the system property.
        if (licenseKey == null || licenseKey == "") {
            logger.fatal("JxBrowser license key not found. You must set the 'JXBROWSER_LICENSE_KEY' environment variable in your .env file in the root of the project.")
            exitProcess(1)
        }

        System.setProperty("jxbrowser.license.key", licenseKey)

        // Set up the browser engine
        // https://teamdev.com/jxbrowser/docs/guides/engine.html
        val engineOptions = EngineOptions.newBuilder(
            RenderingMode.OFF_SCREEN
        )

        // Set the license key for the engine
        engineOptions.licenseKey(licenseKey)

        // Represents an absolute path to the directory where the profiles, and their data such as cache, cookies, history,
        // GPU cache, local storage, visited links, web data, spell checking dictionary files, etc. are stored.
        engineOptions.userDataDir(
            Path("${Env.homeDir}/.jxbrowser")
        )

        // Set default language for the browser engine
        // Note: This doesn't change the language of the web page, but the language of the browser engine itself.
        engineOptions.language(Language.ENGLISH_US)

        // Remote debugging allows you to debug a web page in the browser engine remotely using the Chrome DevTools Protocol.
        engineOptions.addSwitch("--remote-allow-origins=http://localhost:9222")
        engineOptions.remoteDebuggingPort(9222)

        // Custom user agent, to identify the browser engine
        engineOptions.userAgent(userAgent)

        engineOptions.addScheme(
            Scheme.of("https"),
            InterceptUrlRequestCallback { params ->
                val url = params.urlRequest().url()

                if (url.contains("/monitor_browser/") || url.contains("/web/report") || url.contains("tiktokw.us/web/common")){
                    // Intercept the request and return a custom response
                    val job = params.newUrlRequestJob(
                        UrlRequestJob.Options
                            .newBuilder(HttpStatus.NO_CONTENT)
                            .build())
                    job.complete()
                    InterceptUrlRequestCallback.Response.intercept(job)
                }
                else {
                    InterceptUrlRequestCallback.Response.proceed()
                }
            }
        )

        engine = Engine.newInstance(
            engineOptions.build()
        )

        // What to do when the engine crashes?
        engine.on(EngineCrashed::class.java) { event ->
            val exitCode = event.exitCode()
            val message = event.toString()
            logger.fatal("Engine crashed with exit code $exitCode\n$message")
            // TODO: Implement me! I think we should attempt to recreate and restart the engine
        }

        // There's always a default profile created by the engine.
        // We don't want to create a new profiles or manage profiles, so we'll just use the default one.
        val profiles = engine.profiles()
        val profile = profiles.defaultProfile()

        // Disable autofill (good for security)
        profile.preferences().disableAutofill()

        browser = profile.newBrowser()
        browser.on(BrowserClosed::class.java) { _ ->
            logger.info("Browser closed")
        }
        browser.resize(width, height)

        setupKeybindings()

        // Open devtools automatically
        browser.devTools().show()

        // Callback when a remote debugging URL is present/added
        browser.devTools().remoteDebuggingUrl().ifPresent { _ -> }
    }

    private fun setupKeybindings(){
        browser.set(
            PressKeyCallback::class.java,
            PressKeyCallback { params ->
                val event = params.event()
                val isControlDown = event.keyModifiers().isControlDown()

                // Switch statement for key codes
                when (event.keyCode()) {
                    KeyCode.KEY_CODE_F12 -> {
                        // Toggle open or closed dev tools for developers
                        browser.devTools().hide()
                        browser.devTools().show()
                        PressKeyCallback.Response.suppress()
                    }
                    KeyCode.KEY_CODE_F5 -> {
                        // Reload the page
                        browser.navigation().reload()
                        PressKeyCallback.Response.suppress()
                    }
                    KeyCode.KEY_CODE_R -> {
                        if (isControlDown){
                            // Reload the page
                            browser.navigation().reload()
                            PressKeyCallback.Response.suppress()
                        }
                        else {
                            PressKeyCallback.Response.proceed()
                        }
                    }
                    else -> {
                        PressKeyCallback.Response.proceed()
                    }
                }
            }
        )
    }

    fun makeInstance(): BrowserView {
        return BrowserView.newInstance(browser)
    }

    fun waitForDOMElementToExist(selector: String, timeoutSeconds: Long = 30){
        // Wait for the element to exist
        val startTime = System.currentTimeMillis()
        browser.mainFrame().ifPresent { frame ->
            var foundObject: JsObject? = null
            while (foundObject == null) {
                // Check if the timeout has been reached
                if (System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
                    throw RuntimeException("Timeout reached while waiting for the DOM element")
                }
                foundObject = frame.executeJavaScript("""
                    document.querySelector("$selector");
                """.trimIndent())

                if (foundObject == null) {
                    try {
                        // Sleep a bit to prevent high CPU usage and give some time for the element to appear
                        Thread.sleep(500)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw RuntimeException("Interrupted while waiting for DOM element", ie)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    fun shutdown() {
        if (!browser.isClosed){
            browser.close()
        }
        if (!engine.isClosed){
            engine.close()
        }
    }
}
