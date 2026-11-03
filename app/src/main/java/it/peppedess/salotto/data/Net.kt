package it.peppedess.salotto.data

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Client HTTP condiviso (TMDB e immagini). I domini di TMDB vengono risolti
 * con DNS sicuro (Cloudflare, poi Google), così un DNS dell'operatore che li
 * filtra non li blocca; per tutto il resto si usa il DNS di sistema.
 */
object Net {

    private val bootstrap: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val secureDns: List<Dns> by lazy {
        listOf(
            DnsOverHttps.Builder()
                .client(bootstrap)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
                .build(),
            DnsOverHttps.Builder()
                .client(bootstrap)
                .url("https://dns.google/dns-query".toHttpUrl())
                .bootstrapDnsHosts(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"))
                .build(),
        )
    }

    private val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (isTmdb(hostname)) {
                for (resolver in secureDns) {
                    val found = runCatching { resolver.lookup(hostname) }.getOrNull()
                    if (!found.isNullOrEmpty()) return found
                }
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun isTmdb(host: String): Boolean =
        host.endsWith("themoviedb.org") || host.endsWith("tmdb.org")
}
