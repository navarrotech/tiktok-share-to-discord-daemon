package org.navarrotech

import java.awt.BorderLayout

import javax.swing.JFrame
import javax.swing.SwingUtilities

import org.apache.logging.log4j.LogManager

class App : JFrame() {
    private val viewEngine = ViewEngine()
    val logger = LogManager.getLogger(App::class.java)

    val manager = TikTokManager(viewEngine)

    companion object {
        private const val APPLICATION_NAME = "TikTok Scraper"
        private val VERSION = Env.get("APP_VERSION", "dev")

        @JvmStatic
        fun main(args: Array<String>) {
            SwingUtilities.invokeLater {
                App().start()
            }
            Database.migrate()
        }
    }

    init {
        logger.info("Starting Flagship COP" +
            "\n  > Version: $VERSION" +
            "\n  > Commit Hash: ${Env.get("COMMIT_HASH")}" +
            "\n  > Mode: ${if (Env.isProduction) "production" else "development"}" +
            "\n  > User Agent: ${ViewEngine.userAgent}" +
            "\n"
        )

        title = APPLICATION_NAME
        setSize(viewEngine.width, viewEngine.height)
        setLocationRelativeTo(null) // Centers on screen // TODO: In the future, this should be set to the last known location of the window.
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
    }

    fun start() {
        // Add the primary view:
        add(
            viewEngine.makeInstance(),
            BorderLayout.CENTER
        )

        // Make the app visible (final step)
        isVisible = true

        // Take control of the browser:
        manager.takeControl()
    }

    override fun dispose() {
        viewEngine.shutdown()
        super.dispose()
    }
}
