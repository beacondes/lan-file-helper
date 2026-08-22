package com.beacondes.lanfilehelper

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val prefs by lazy { getSharedPreferences("config", MODE_PRIVATE) }

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
            .setMessage("电脑上运行 npm start 后打印的地址，去掉 http:// 和 :3000")
            .setView(input)
            .setPositiveButton("连接") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) {
                    prefs.edit().putString("ip", ip).apply()
                    load(ip)
                }
            }
            .setNegativeButton("取消", null)
            .show()
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

    companion object {
        private const val FILE_CHOOSER_REQUEST = 1001
    }
}
