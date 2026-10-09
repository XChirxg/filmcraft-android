package ai.storyteller.filmcraft

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var server: LocalAssetServer? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val clipData = result.data?.clipData
            val data = result.data?.data
            val uris = when {
                clipData != null -> Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                data != null -> arrayOf(data)
                else -> null
            }
            filePathCallback?.onReceiveValue(uris)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while editing video
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fullscreen immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        // Start embedded offline HTTP server
        val localServer = LocalAssetServer(this)
        val port = localServer.start()
        server = localServer

        setupWebView(port)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(port: Int) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(false)

        // Hardware acceleration & focus
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e(
                    "FilmCraft",
                    "WebView error: ${error?.description} (${error?.errorCode}) for ${request?.url}"
                )
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                if (url.host == "127.0.0.1" || url.host == "localhost") {
                    var path = url.path ?: "/"
                    if (path == "/" || path.isEmpty()) path = "/index.html"
                    val assetPath = "web" + (if (path.startsWith("/")) path else "/$path")
                    val mimeType = when {
                        path.endsWith(".wasm") -> "application/wasm"
                        path.endsWith(".js") || path.endsWith(".mjs") -> "text/javascript"
                        path.endsWith(".html") -> "text/html"
                        path.endsWith(".css") -> "text/css"
                        path.endsWith(".png") -> "image/png"
                        path.endsWith(".svg") -> "image/svg+xml"
                        path.endsWith(".json") -> "application/json"
                        path.endsWith(".txt") -> "text/plain"
                        else -> "application/octet-stream"
                    }
                    val encoding = if (mimeType.startsWith("text/") || mimeType == "application/json") "UTF-8" else null
                    return try {
                        val stream = assets.open(assetPath)
                        val headers = mapOf(
                            "Access-Control-Allow-Origin" to "*",
                            "Cache-Control" to "no-cache"
                        )
                        WebResourceResponse(mimeType, encoding, 200, "OK", headers, stream)
                    } catch (e: Exception) {
                        null
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d(
                    "FilmCraftWeb",
                    "${consoleMessage?.message()} -- line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}"
                )
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf("video/*", "audio/*", "image/*")
                    )
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                filePickerLauncher.launch(Intent.createChooser(intent, "Import Media to FilmCraft"))
                return true
            }
        }

        // Load FilmCraft from local server (WebGL2 / WebGPU automatically handled)
        webView.loadUrl("http://127.0.0.1:$port/index.html")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Pass all hardware keyboard shortcuts directly to WebView
        if (webView.dispatchKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        webView.destroy()
    }
}
