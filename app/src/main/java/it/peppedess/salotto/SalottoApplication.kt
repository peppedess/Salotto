package it.peppedess.salotto

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import it.peppedess.salotto.data.Net

class SalottoApplication : Application(), SingletonImageLoader.Factory {

    /** Le immagini passano dallo stesso client di TMDB (con DNS sicuro). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { Net.client }))
            }
            .crossfade(true)
            .build()
}
