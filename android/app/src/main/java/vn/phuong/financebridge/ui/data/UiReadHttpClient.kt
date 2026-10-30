package vn.phuong.financebridge.ui.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared connection pool for authenticated UI reads. */
object UiReadHttpClient {
    val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
}
