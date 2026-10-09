package ai.storyteller.filmcraft

import android.content.Context
import android.util.Log
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class LocalAssetServer(private val context: Context) {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newFixedThreadPool(4)
    @Volatile private var isRunning = false
    var port: Int = 0
        private set

    fun start(): Int {
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        port = socket.localPort
        isRunning = true

        executor.execute {
            while (isRunning) {
                try {
                    val client = socket.accept()
                    executor.execute { handleClient(client) }
                } catch (e: Exception) {
                    if (!isRunning) break
                }
            }
        }
        return port
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        executor.shutdown()
    }

    private fun handleClient(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val line = reader.readLine() ?: return
            val parts = line.split(" ")
            if (parts.size < 2 || parts[0] != "GET") {
                sendError(client, 400, "Bad Request")
                return
            }

            var path = parts[1]
            if (path.contains("?")) {
                path = path.substringBefore("?")
            }
            if (path.contains("#")) {
                path = path.substringBefore("#")
            }
            if (path == "/" || path.isEmpty()) {
                path = "/index.html"
            }

            val assetPath = "web" + (if (path.startsWith("/")) path else "/$path")
            val mimeType = getMimeType(assetPath)

            try {
                // Try reading asset
                val length: Long = try {
                    context.assets.openFd(assetPath).length
                } catch (_: Exception) {
                    -1L
                }

                context.assets.open(assetPath).use { inputStream ->
                    val output = BufferedOutputStream(client.getOutputStream())
                    val header = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: $mimeType\r\n")
                        if (length > 0) {
                            append("Content-Length: $length\r\n")
                        }
                        append("Access-Control-Allow-Origin: *\r\n")
                        append("Cache-Control: no-cache\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    output.write(header.toByteArray(Charsets.UTF_8))
                    inputStream.copyTo(output)
                    output.flush()
                }
            } catch (e: Exception) {
                sendError(client, 404, "Not Found")
            }
        } catch (e: Exception) {
            Log.e("LocalAssetServer", "Error handling client", e)
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun sendError(client: Socket, code: Int, message: String) {
        try {
            val output = client.getOutputStream()
            val response = "HTTP/1.1 $code $message\r\nContent-Type: text/plain\r\nContent-Length: ${message.length}\r\nConnection: close\r\n\r\n$message"
            output.write(response.toByteArray(Charsets.UTF_8))
            output.flush()
        } catch (_: Exception) {}
    }

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".wasm") -> "application/wasm"
            path.endsWith(".js") || path.endsWith(".mjs") -> "text/javascript; charset=utf-8"
            path.endsWith(".html") -> "text/html; charset=utf-8"
            path.endsWith(".css") -> "text/css; charset=utf-8"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".txt") -> "text/plain; charset=utf-8"
            else -> "application/octet-stream"
        }
    }
}
