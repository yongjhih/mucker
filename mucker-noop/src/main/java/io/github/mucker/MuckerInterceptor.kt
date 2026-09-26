package io.github.mucker

import okhttp3.Interceptor
import okhttp3.Response

/**
 * No-op version of MuckerInterceptor that directly passes requests through to the network.
 */
class MuckerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}
