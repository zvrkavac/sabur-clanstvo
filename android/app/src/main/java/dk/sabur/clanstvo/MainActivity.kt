package dk.sabur.clanstvo

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Glavni ekran: članski portal https://dzemat.sabur.dk/clanstvo/ u WebView-u.
 *  - linkovi izvan /clanstvo (MobilePay, Viber, telefon, SMS, e-mail, Google Maps…) otvaraju se u odgovarajućoj aplikaciji
 *  - povuci-dolje za osvježavanje, offline ekran, nazad dugme kroz historiju
 *  - window.print() (potvrde, pristupnica) → Android ispis / PDF
 *  - JS most "SaburApp" (Bridge.kt) za obavijesti i nadogradnje
 */
class MainActivity : AppCompatActivity() {

    lateinit var web: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: ProgressBar
    private lateinit var offline: View
    private var keepSplash = true
    private var lastErrorUrl: String? = null

    // Dok je aplikacija otvorena: provjera novih obavijesti svakih 60 s (u pozadini radi WorkManager)
    private val poll = Handler(Looper.getMainLooper())
    private val pollTask = object : Runnable {
        override fun run() {
            InboxWorker.runNow(this@MainActivity)
            poll.postDelayed(this, 60_000L)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { keepSplash }
        Handler(Looper.getMainLooper()).postDelayed({ keepSplash = false }, 4000)   // sigurnosno
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        swipe = findViewById(R.id.swipe)
        progress = findViewById(R.id.progress)
        offline = findViewById(R.id.offline)
        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.teal))
        swipe.setOnRefreshListener { web.reload() }
        swipe.setOnChildScrollUpCallback { _, _ -> web.scrollY > 0 }
        findViewById<Button>(R.id.retry).setOnClickListener {
            offline.visibility = View.GONE
            web.loadUrl(lastErrorUrl ?: BuildConfig.BASE_URL + "?source=app")
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            userAgentString = "$userAgentString SaburApp/${BuildConfig.VERSION_NAME}"
        }
        web.addJavascriptInterface(Bridge(this), "SaburApp")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleUrl(request.url)

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                keepSplash = false
                progress.visibility = View.GONE
                swipe.isRefreshing = false
                // window.print() → Android ispis (potvrda o članstvu, pristupnica)
                view.evaluateJavascript("window.print=function(){try{SaburApp.print()}catch(e){}};", null)
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    lastErrorUrl = request.url.toString()
                    keepSplash = false
                    swipe.isRefreshing = false
                    offline.visibility = View.VISIBLE
                }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                if (newProgress >= 100) progress.visibility = View.GONE
            }
        }
        // Preuzimanja (npr. Excel izvještaj u adminu) — s kolačićima prijave
        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val req = DownloadManager.Request(Uri.parse(url))
                    .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    .addRequestHeader("User-Agent", userAgent)
                    .setMimeType(mimeType)
                    .setTitle(name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name)
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                openExternal(Uri.parse(url))
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (offline.visibility == View.VISIBLE) { offline.visibility = View.GONE; finish() }
            else if (web.canGoBack()) web.goBack() else finish()
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(startUrl(intent))

        Notifier.createChannel(this)
        askNotificationPermission()
        InboxWorker.schedule(this)
        Updater.check(this, silent = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val u = intent.getStringExtra(EXTRA_URL) ?: intent.data?.toString()
        if (u != null) web.loadUrl(startUrl(intent))
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        poll.removeCallbacks(pollTask)
        poll.postDelayed(pollTask, 1500L)
    }

    override fun onPause() {
        poll.removeCallbacks(pollTask)
        web.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    /** URL iz obavijesti / linka ili početna stranica */
    private fun startUrl(i: Intent?): String {
        val extra = i?.getStringExtra(EXTRA_URL)
        if (!extra.isNullOrBlank()) return absolute(extra)
        val data = i?.data
        if (data != null && isOwn(data)) return data.toString()
        return BuildConfig.BASE_URL + "?source=app"
    }

    fun absolute(u: String): String =
        if (u.startsWith("https://")) u else BuildConfig.BASE_URL + u.trimStart('/')

    private fun isOwn(u: Uri): Boolean =
        u.scheme == "https" && u.host == "dzemat.sabur.dk" && (u.path ?: "").startsWith("/clanstvo")

    /** true = mi smo obradili (otvoreno izvan WebView-a) */
    private fun handleUrl(u: Uri): Boolean {
        if (isOwn(u)) return false
        val scheme = u.scheme ?: ""
        if (scheme == "intent") {
            return try {
                val it = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                startActivity(it); true
            } catch (e: Exception) {
                val fb = try { Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url") } catch (x: Exception) { null }
                if (fb != null) openExternal(Uri.parse(fb)); true
            }
        }
        openExternal(u)    // MobilePay, Viber, tel:, sms:, mailto:, Google Maps, ostali sajtovi
        return true
    }

    fun openExternal(u: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show()
        }
    }

    fun printPage() {
        val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val name = "Sabur - " + (web.title ?: "dokument")
        pm.print(name, web.createPrintDocumentAdapter(name), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
