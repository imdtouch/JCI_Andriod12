package com.example.jci_andriod12

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.URL

const val GITHUB_OWNER = "imdtouch"
const val GITHUB_REPO = "JCI_Andriod12"

class UpdateManager(private val context: Context) {
    
    private val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
    
    data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val checksumUrl: String?)
    sealed class UpdateResult {
        data class Available(val info: UpdateInfo) : UpdateResult()
        object UpToDate : UpdateResult()
        object NoInternet : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }
    
    private fun getInternetNetwork(): android.net.Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Try WiFi first for internet
        cm.allNetworks.forEach { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@forEach
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && 
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return network
            }
        }
        // Fall back to any network with internet
        cm.allNetworks.forEach { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@forEach
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return network
            }
        }
        return null
    }
    
    fun hasInternet(): Boolean = getInternetNetwork() != null
    
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
    
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        val network = getInternetNetwork() ?: return@withContext UpdateResult.NoInternet
        
        try {
            val apiUrl = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
            val json = network.openConnection(URL(apiUrl)).apply {
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10000
                readTimeout = 10000
            }.getInputStream().bufferedReader().readText()
            
            val release = JSONObject(json)
            val tagName = release.getString("tag_name")
            val remoteVersionName = tagName.removePrefix("v")
            val currentVersionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
            
            if (compareVersions(remoteVersionName, currentVersionName) <= 0) return@withContext UpdateResult.UpToDate
            
            val assets = release.getJSONArray("assets")
            var apkUrl: String? = null
            var checksumUrl: String? = null
            
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                when {
                    name.endsWith(".apk") -> apkUrl = asset.getString("browser_download_url")
                    name == "checksums.txt" -> checksumUrl = asset.getString("browser_download_url")
                }
            }
            
            if (apkUrl != null) {
                val versionCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt() + 1
                UpdateResult.Available(UpdateInfo(versionCode, remoteVersionName, apkUrl, checksumUrl))
            } else {
                UpdateResult.Error("No APK in release")
            }
        } catch (e: java.net.UnknownHostException) {
            UpdateResult.NoInternet
        } catch (e: java.net.SocketTimeoutException) {
            UpdateResult.Error("Connection timed out")
        } catch (e: Exception) {
            UpdateResult.Error("Connection failed")
        }
    }
    
    suspend fun downloadAndInstall(update: UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        val network = getInternetNetwork() ?: return@withContext false
        try {
            val tempFile = File(updatesDir, "temp.apk")
            
            network.openConnection(URL(update.apkUrl)).getInputStream().use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            
            installApkSilently(tempFile)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun installApkSilently(apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        apkFile.inputStream().use { input ->
            session.openWrite("update.apk", 0, apkFile.length()).use { output ->
                input.copyTo(output)
                session.fsync(output)
            }
        }

        val intent = Intent(context, UpdateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        session.commit(pendingIntent.intentSender)
    }
}
