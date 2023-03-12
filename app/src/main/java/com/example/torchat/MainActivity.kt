package com.example.torchat

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.example.torchat.ui.theme.TorchatTheme
import org.torproject.jni.TorService
import org.torproject.jni.TorService.LocalBinder
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

class MainActivity : ComponentActivity() {

    private val WEB_URL = "http://haystak5njsmn2hqkewecpaxetahtwhsbsa64jom2k22z5afxhnpxfid.onion"
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //checkForInternet(this)
        setContent {
            TorchatTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val st = remember {
                        mutableStateOf("")
                    }
                    torConnect(this, st)
                    Log.d("STSTATUS", st.value)
                    if(st.value == "TOR_CONNECTED") {
                        AndroidView(factory = {
                            WebView(it).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                webViewClient = object: WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse {
                                        val urlString = request?.url.toString().split("#".toRegex()).toTypedArray()[0]
                                        try{
                                            val connection: HttpURLConnection
                                            val proxied = true
                                            connection = if(proxied) {
                                                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("localhost", 9050))
                                                URL(urlString).openConnection(proxy) as HttpURLConnection

                                            } else {
                                                URL(urlString).openConnection() as HttpURLConnection
                                            }
                                            connection.requestMethod = request?.method
                                            if (request != null) {
                                                for((key, value) in request.requestHeaders) {
                                                    connection.setRequestProperty(key, value)
                                                }
                                            }
                                            val `in`: InputStream = BufferedInputStream(connection.inputStream)
                                            val encoding = connection.contentEncoding
                                            connection.headerFields
                                            val responseHeaders: MutableMap<String, String> = HashMap()
                                            for(key in connection.headerFields.keys) {
                                                if(key != null && key.isNotEmpty()){
                                                    responseHeaders[key] = connection.getHeaderField(key)
                                                }
                                            }
                                            var mimeType = "text/plain"
                                            if(connection.contentType != null && connection.contentType.isNotEmpty()) {
                                                mimeType = connection.contentType.split("; ".toRegex()).toTypedArray()[0]
                                            }
                                            return WebResourceResponse(
                                                mimeType,
                                                encoding,
                                                connection.responseCode,
                                                connection.responseMessage,
                                                responseHeaders,
                                                `in`
                                            )

                                        } catch (e: UnsupportedEncodingException) {
                                            e.printStackTrace()
                                        } catch (e: IOException) {
                                            e.printStackTrace()
                                        }
                                        return WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            204,
                                            "No Content",
                                            HashMap(),
                                            ByteArrayInputStream(byteArrayOf())
                                        )
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        super.onPageStarted(view, url, favicon)

                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)

                                    }

                                }

                                settings.javaScriptEnabled = true
                                loadUrl(WEB_URL)
                            }
                        })
                    } else {
                        Text(text="Tor is connecting")
                    }

                }
            }
        }
    }

    private fun checkForInternet(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false

            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }

        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    private fun torConnect(context: Context, status: MutableState<String>) {
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                status.value = intent.getStringExtra(TorService.EXTRA_STATUS)
                    ?: "Not connected"
                Log.d("AJEETONRECEIVE", status.value)
            }
        }, IntentFilter(TorService.ACTION_STATUS))
        context.bindService(
            Intent(context,TorService::class.java),
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val torService = (service as LocalBinder).service
                    while(torService.torControlConnection == null) {
                        try {
                            Thread.sleep(500)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                    }
                    status.value = "TOR_CONNECTED"
                    Log.d("AJEET", status.value)
                }

                override fun onServiceDisconnected(p0: ComponentName?) {
                    status.value =  "Tor is disconnected"
                    Log.d("AJEETDIS", status.value)
                }
            },
            BIND_AUTO_CREATE
        )
    }
}





@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TorchatTheme {
        Greeting("Android")
    }
}

