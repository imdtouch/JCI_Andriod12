package com.example.jci_andriod12

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest

// GitHub repository configuration
const val GITHUB_OWNER = "imdtouch"
const val GITHUB_REPO = "JCI_Andriod12"
const val MAX_STORED_VERSIONS = 10

class UpdateManager(private val context: Context) {
    
    private val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
    private val versionsFile = File(updatesDir, "versions.json")
    
    data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val checksumUrl: String?)
    data class StoredVersion(val code: Int, val name: String, val file: String, val date: Long)
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
            val finalFile = File(updatesDir, "v${update.versionCode}.apk")
            
            // Download APK via WiFi/internet network
            network.openConnection(URL(update.apkUrl)).getInputStream().use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            
            // Verify checksum if available
            if (update.checksumUrl != null) {
                try {
                    val checksums = network.openConnection(URL(update.checksumUrl)).getInputStream().bufferedReader().readText()
                    // Find a 64-char hex string (SHA256)
                    val hashRegex = Regex("[a-fA-F0-9]{64}")
                    val expectedHash = hashRegex.find(checksums)?.value?.lowercase()
                    
                    if (expectedHash != null) {
                        val actualHash = tempFile.sha256()
                        if (actualHash != expectedHash) {
                            tempFile.delete()
                            return@withContext false
                        }
                    }
                } catch (_: Exception) {
                    // Skip verification if checksum fetch fails
                }
            }
            
            // Move to final location and save metadata
            tempFile.renameTo(finalFile)
            saveVersion(StoredVersion(update.versionCode, update.versionName, finalFile.name, System.currentTimeMillis()))
            pruneOldVersions()
            
            installApkSilently(finalFile)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getStoredVersions(): List<StoredVersion> {
        if (!versionsFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(versionsFile.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                StoredVersion(obj.getInt("code"), obj.getString("name"), obj.getString("file"), obj.getLong("date"))
            }.filter { File(updatesDir, it.file).exists() }.sortedByDescending { it.code }
        } catch (e: Exception) { emptyList() }
    }
    
    suspend fun installStoredVersion(versionCode: Int): Boolean = withContext(Dispatchers.IO) {
        val version = getStoredVersions().find { it.code == versionCode } ?: return@withContext false
        val apkFile = File(updatesDir, version.file)
        if (!apkFile.exists()) return@withContext false
        val currentCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        installApkSilently(apkFile, allowDowngrade = versionCode < currentCode)
        true
    }
    
    private fun saveVersion(version: StoredVersion) {
        val versions = getStoredVersions().toMutableList()
        versions.removeAll { it.code == version.code }
        versions.add(0, version)
        val arr = JSONArray()
        versions.forEach { v ->
            arr.put(JSONObject().apply {
                put("code", v.code)
                put("name", v.name)
                put("file", v.file)
                put("date", v.date)
            })
        }
        versionsFile.writeText(arr.toString())
    }
    
    private fun pruneOldVersions() {
        val versions = getStoredVersions()
        if (versions.size > MAX_STORED_VERSIONS) {
            versions.drop(MAX_STORED_VERSIONS).forEach { old ->
                File(updatesDir, old.file).delete()
            }
            val arr = JSONArray()
            versions.take(MAX_STORED_VERSIONS).forEach { v ->
                arr.put(JSONObject().apply {
                    put("code", v.code)
                    put("name", v.name)
                    put("file", v.file)
                    put("date", v.date)
                })
            }
            versionsFile.writeText(arr.toString())
        }
    }
    
    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    private fun installApkSilently(apkFile: File, allowDowngrade: Boolean = false) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        
        if (allowDowngrade) {
            // Set INSTALL_ALLOW_DOWNGRADE flag (0x80) via reflection
            try {
                val method = params.javaClass.getDeclaredMethod("setInstallFlags", Int::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(params, 0x00000080 or 0x00000002) // ALLOW_DOWNGRADE | REPLACE_EXISTING
            } catch (e: Exception) { }
        }

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
