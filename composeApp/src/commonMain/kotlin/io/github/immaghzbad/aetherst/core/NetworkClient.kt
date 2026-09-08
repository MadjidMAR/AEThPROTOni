package io.github.immaghzbad.aetherst.shared.core

import okhttp3.OkHttpClient

object NetworkClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
