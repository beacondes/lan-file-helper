package com.beacondes.lanfilehelper

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val prefs by lazy { getSharedPreferences("config", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private var pendingShareIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent() ?: return false
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        findViewById<android.view.View>(R.id.title_bar).setOnClickListener { showIpDialog() }

        val savedIp = prefs.getString("ip", "")
        if (savedIp.isNullOrEmpty()) {
            showIpDialog()
        } else {
            load(savedIp)
        }

        // 处理从系统分享进来的内容
        handleShareIntent(intent)
    }

    private fun load(ip: String) {
        val url = if (ip.startsWith("http://") || ip.startsWith("https://")) ip else "http://$ip:3000"
        webView.loadUrl(url)
        findViewById<TextView>(R.id.title_text).text = "文件助手 · $ip"
    }

    private fun showIpDialog() {
        val input = EditText(this)
        input.hint = "例如 192.168.1.100"
        input.setText(prefs.getString("ip", ""))
        AlertDialog.Builder(this)
            .setTitle("输入电脑的局域网 IP")
            .setMessage("电脑上运行服务后打印的地址，去掉 http:// 和 :3000")
            .setView(input)
            .setPositiveButton("连接") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) {
                    prefs.edit().putString("ip", ip).apply()
                    load(ip)
                    pendingShareIntent?.let { handleShareIntent(it) }
                    pendingShareIntent = null
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // —— 处理系统分享 ——
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) return
        val ip = prefs.getString("ip", "")
        if (ip.isNullOrEmpty()) {
            pendingShareIntent = intent
            return
        }
        when (intent.action) {
            Intent.ACTION_SEND -> handleSingleShare(intent, ip)
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleShare(intent, ip)
        }
    }

    private fun handleSingleShare(intent: Intent, ip: String) {
        val stream = getStreamUri(intent)
        if (stream != null) {
            uploadFile(ip, stream)
        } else {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) sendText(ip, text)
        }
    }

    @Suppress("DEPRECATION")
    private fun getStreamUri(intent: Intent): Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)

    private fun handleMultipleShare(intent: Intent, ip: String) {
        val uris = mutableListOf<Uri>()
        intent.clipData?.let { cd ->
            for (i in 0 until cd.itemCount) {
                cd.getItemAt(i).uri?.let { uris.add(it) }
            }
        }
        if (uris.isEmpty()) getStreamUri(intent)?.let { uris.add(it) }
        uris.forEach { uploadFile(ip, it) }
    }

    private fun uploadFile(ip: String, uri: Uri) {
        executor.execute {
            try {
                val name = queryName(uri)
                val url = URL("http://$ip:3000/api/upload")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 60000
                val boundary = "----LanFileHelper" + System.currentTimeMillis()
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                conn.outputStream.use { out ->
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n".toByteArray())
                    out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                    contentResolver.openInputStream(uri)?.use { it.copyTo(out) }
                    out.write("\r\n--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"device\"\r\n\r\n".toByteArray())
                    out.write("$deviceId\r\n".toByteArray())
                    out.write("--$boundary--\r\n".toByteArray())
                }

                val code = conn.responseCode
                toastOnUi(if (code == 200) "已发送：$name" else "发送失败（$code）")
            } catch (e: Exception) {
                toastOnUi("发送失败：${e.message ?: "网络错误"}")
            }
        }
    }

    private fun sendText(ip: String, text: String) {
        executor.execute {
            try {
                val conn = URL("http://$ip:3000/api/message").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject().put("content", text).put("device", deviceId).toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                toastOnUi(if (code == 200) "已发送文本" else "发送失败（$code）")
            } catch (e: Exception) {
                toastOnUi("发送失败：${e.message ?: "网络错误"}")
            }
        }
    }

    private fun queryName(uri: Uri): String {
        var name = "shared_file"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx)
                }
            }
        } catch (e: Exception) {}
        return name
    }

    private fun toastOnUi(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    private val deviceId: String
        get() {
            var id = prefs.getString("deviceId", "")
            if (id.isEmpty()) {
                id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                    ?: "设备-" + (1000..9999).random()
                prefs.edit().putString("deviceId", id).apply()
            }
            return id
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            fileChooserCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            )
            fileChooserCallback = null
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    companion object {
        private const val FILE_CHOOSER_REQUEST = 1001
    }
}
