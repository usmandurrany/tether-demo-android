package io.github.usmandurrany.tether.demo.stability

import java.net.HttpURLConnection
import java.net.URL

/**
 * A small client for Toxiproxy's HTTP API (https://github.com/Shopify/toxiproxy).
 * The CI script runs Toxiproxy between the emulator and the XMPP server, so a
 * test can make the link slow, narrow, choppy or dead on purpose.
 */
class Toxiproxy(private val api: String, private val proxy: String) {
    /** Removes every toxic and re-enables every proxy. */
    fun reset() {
        call("POST", "/reset", null)
    }

    /** Disabled, the proxy closes every connection and refuses new ones: the server is unreachable. */
    fun enabled(on: Boolean) {
        call("POST", "/proxies/$proxy", """{"enabled":$on}""")
    }

    /**
     * Adds a toxic in both directions. Types and attributes are Toxiproxy's,
     * for example `latency` with `latency` and `jitter` in ms, or `bandwidth`
     * with `rate` in KB/s.
     */
    fun add(name: String, type: String, attributes: Map<String, Number>) {
        for (stream in listOf("upstream", "downstream")) {
            val attrs = attributes.entries.joinToString(",") { (k, v) -> "\"$k\":$v" }
            call(
                "POST",
                "/proxies/$proxy/toxics",
                """{"name":"$name-$stream","type":"$type","stream":"$stream","toxicity":1.0,"attributes":{$attrs}}""",
            )
        }
    }

    fun remove(name: String) {
        for (stream in listOf("upstream", "downstream")) call("DELETE", "/proxies/$proxy/toxics/$name-$stream", null)
    }

    private fun call(method: String, path: String, body: String?): String {
        val connection = URL(api + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        try {
            val code = connection.responseCode
            val text = (if (code < 400) connection.inputStream else connection.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
            check(code < 400) { "Toxiproxy $method $path failed: $code $text" }
            return text
        } finally {
            connection.disconnect()
        }
    }
}
