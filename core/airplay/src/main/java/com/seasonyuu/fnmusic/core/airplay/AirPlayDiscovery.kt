package com.seasonyuu.fnmusic.core.airplay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.Inet4Address
import java.util.Locale

import com.seasonyuu.fnmusic.core.model.AirPlayDevice

internal fun normalizeDeviceId(value: String): String =
    value.replace(":", "").replace("-", "").uppercase(Locale.ROOT)
        .takeIf { it.length == 12 && it.all { c -> c in '0'..'9' || c in 'A'..'F' } }.orEmpty()

internal fun discoverySnapshot(devices: Collection<AirPlayDevice>): List<AirPlayDevice> = devices
    .sortedBy { if (it.serviceType.contains("_airplay")) 0 else 1 }
    .distinctBy { it.id }

/** Own one instance per discovery session. Call close even when discovery/resolve fails. */
class AirPlayDiscovery(context: Context) : AutoCloseable {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val services = linkedMapOf<String, AirPlayDevice>()
    private val liveServices = mutableSetOf<String>()
    private fun serviceKey(info: NsdServiceInfo) = info.serviceType.trimEnd('.') + "/" + info.serviceName
    private var started = false
    private var generation = 0
    private var resolvePending = false
    private val queue = ArrayDeque<Pair<Int, NsdServiceInfo>>()

    @Synchronized
    fun start(onDevices: (List<AirPlayDevice>) -> Unit, onError: (String) -> Unit) {
        check(!started) { "Use a new discovery instance for each session" }
        started = true
        val token = generation
        for (type in listOf("_airplay._tcp.", "_raop._tcp.")) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    synchronized(this@AirPlayDiscovery) {
                        if (generation == token) onError("discovery_failed")
                    }
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onServiceLost(info: NsdServiceInfo) {
                    synchronized(this@AirPlayDiscovery) {
                        if (generation != token) return
                        liveServices.remove(serviceKey(info))
                        services.remove(serviceKey(info))
                        onDevices(snapshot())
                    }
                }
                override fun onServiceFound(info: NsdServiceInfo) {
                    synchronized(this@AirPlayDiscovery) {
                        if (generation != token) return
                        liveServices.add(serviceKey(info))
                        queue.addLast(token to info)
                        resolveNext(onDevices, onError)
                    }
                }
            }
            listeners += listener
            try { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
            catch (_: SecurityException) { onError("local_network_permission_required") }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext(onDevices: (List<AirPlayDevice>) -> Unit, onError: (String) -> Unit) {
        if (resolvePending || queue.isEmpty()) return
        val (token, service) = queue.removeFirst()
        resolvePending = true
        val callback = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = finish(null)
            override fun onServiceResolved(info: NsdServiceInfo) = finish(info)
            private fun finish(info: NsdServiceInfo?) {
                synchronized(this@AirPlayDiscovery) {
                    resolvePending = false
                    if (generation == token && info != null && serviceKey(service) in liveServices) {
                        val txt = info.attributes.mapValues { it.value?.toString(Charsets.UTF_8).orEmpty() }
                        val id = normalizeDeviceId(txt["deviceid"] ?: info.serviceName.substringBefore('@'))
                        val host = if (android.os.Build.VERSION.SDK_INT >= 34)
                            info.hostAddresses.firstOrNull { it is Inet4Address } else info.host
                        if (id.isNotEmpty() && host is Inet4Address) {
                            services[serviceKey(service)] = AirPlayDevice(
                                id, info.serviceName.substringAfter('@'), host.hostAddress.orEmpty(), info.port,
                                txt["model"] ?: txt["am"].orEmpty(), txt["features"] ?: txt["ft"].orEmpty(), info.serviceType,
                            )
                            onDevices(snapshot())
                        }
                    }
                    resolveNext(onDevices, onError)
                }
            }
        }
        try { nsd.resolveService(service, callback) }
        catch (_: SecurityException) {
            resolvePending = false
            if (generation == token) onError("local_network_permission_required")
        }
    }

    private fun snapshot(): List<AirPlayDevice> = discoverySnapshot(services.values)

    @Synchronized
    override fun close() {
        generation++
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        listeners.clear()
        services.clear()
        liveServices.clear()
        queue.clear()
        // An older resolve may still complete; it owns resolvePending until its callback.
    }
}
