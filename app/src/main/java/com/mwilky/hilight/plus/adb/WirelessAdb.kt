package com.mwilky.hilight.plus.adb

import android.app.Application
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.annotation.WorkerThread
import com.mwilky.hilight.plus.BuildConfig
import com.mwilky.hilight.plus.DebugLog
import com.mwilky.hilight.plus.core.DaemonMain
import com.mwilky.hilight.plus.core.HiLightDaemonService
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Talks to the phone's own `adbd` over Wireless debugging: pairs once with the six-digit code, then
 * connects with the same key whenever the daemon needs starting. Only ever connects to this phone
 * (loopback); nothing leaves the device.
 *
 * The key lives in no-backup storage, so it never leaves the phone in a backup either.
 */
class WirelessAdb private constructor(private val app: Application) {

    sealed interface StartResult {
        data object Started : StartResult
        /** The phone no longer trusts our key (pairing removed, or never done). */
        data object PairingRequired : StartResult
        data class Failed(val message: String) : StartResult
        /**
         * Wireless debugging isn't announcing a port to connect to: it's off, still starting, or
         * switched on but not allowed on this Wi-Fi network (which it can stay in, looking on).
         */
        data object NotReachable : StartResult
    }

    private val dir = File(app.noBackupFilesDir, "adb")
    private val keyFile = File(dir, "key.der")
    private val certFile = File(dir, "cert.der")
    private val pairedMarker = File(dir, "paired")
    private val bootMarker = File(dir, "daemon_boot")
    private val lock = Any()

    fun isPaired(): Boolean = pairedMarker.exists()

    fun forgetPairing() {
        pairedMarker.delete()
    }

    /** Whether a daemon was running at some point since the phone last booted. */
    fun ranThisBoot(): Boolean = runCatching { bootMarker.readText().trim().toInt() == bootCount() }.getOrDefault(false)

    fun markRanThisBoot() {
        runCatching {
            dir.mkdirs()
            bootMarker.writeText(bootCount().toString())
        }
    }

    private fun bootCount(): Int = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /** Pairs with the code shown under "Pair device with pairing code". Throws on a wrong code. */
    @WorkerThread
    fun pair(port: Int, code: String) = synchronized(lock) {
        newManager().use { manager ->
            if (!manager.pair(port, code)) throw IllegalStateException("Pairing was refused")
        }
        dir.mkdirs()
        pairedMarker.createNewFile()
        DebugLog.i(TAG, "Paired with Wireless debugging")
    }

    /**
     * Connects to Wireless debugging and starts [DaemonMain] as the shell user. The daemon then
     * hands its binder to the app by itself; this only reports whether the start command ran.
     */
    @WorkerThread
    fun startDaemon(): StartResult = synchronized(lock) {
        val manager = runCatching { newManager() }.getOrElse {
            DebugLog.e(TAG, "Couldn't load the pairing key", it)
            return StartResult.Failed("Couldn't load the pairing key")
        }
        try {
            val connected = try {
                manager.connectTls(app, CONNECT_TIMEOUT_MS)
            } catch (_: AdbPairingRequiredException) {
                forgetPairing()
                return StartResult.PairingRequired
            }
            if (!connected) return StartResult.NotReachable
            val output = manager.openStream("shell:" + launchCommand()).use(::readUntilClosed).trim()
            if (output.isNotEmpty()) DebugLog.w(TAG, "Daemon start said: $output")
            DebugLog.i(TAG, "Daemon start command sent")
            StartResult.Started
        } catch (t: Throwable) {
            DebugLog.e(TAG, "Starting the daemon failed", t)
            // libadb gives up looking for the port with this message; there's no specific type.
            if (t.message?.contains("find a valid host address and port") == true) {
                StartResult.NotReachable
            } else {
                StartResult.Failed(t.message ?: t.javaClass.simpleName)
            }
        } finally {
            runCatching { manager.close() }
        }
    }

    /**
     * The shell's output until the command ends. libadb sometimes reports the end of a closed
     * stream as an IOException ("Stream closed") rather than end-of-file; either way it's done.
     */
    private fun readUntilClosed(stream: AdbStream): String {
        val output = StringBuilder()
        val buffer = ByteArray(1024)
        try {
            val input = stream.openInputStream()
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.append(String(buffer, 0, count))
            }
        } catch (_: IOException) {
            // Closed: the command has finished.
        }
        return output.toString()
    }

    /**
     * `setsid` detaches the daemon from the ADB shell session, so it keeps running after this
     * connection closes. The shell then stays open for a moment: when it exits, the session is torn
     * down along with anything still in it, and a daemon that hasn't reached `setsid` yet would go
     * with it. The APK path is world-readable, which is all `app_process` needs.
     */
    private fun launchCommand(): String {
        val apk = app.applicationInfo.sourceDir
        val name = "${BuildConfig.APPLICATION_ID}:${HiLightDaemonService.PROCESS_SUFFIX}"
        return "CLASSPATH='$apk' setsid app_process /system/bin --nice-name='$name' " +
            "${DaemonMain::class.java.name} ${Process.myUid()} </dev/null >/dev/null 2>&1 & sleep 1"
    }

    private fun newManager(): Manager {
        val (key, cert) = loadOrCreateKey()
        return Manager(key, cert).apply {
            setApi(Build.VERSION.SDK_INT)
            setHostAddress(LOOPBACK)
            setTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun loadOrCreateKey(): Pair<PrivateKey, Certificate> {
        if (keyFile.exists() && certFile.exists()) {
            val loaded = runCatching {
                val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
                key to cert
            }.onFailure { DebugLog.w(TAG, "Stored key unreadable; making a new one", it) }.getOrNull()
            if (loaded != null) return loaded
        }
        // A new key means the phone no longer knows us.
        forgetPairing()
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = selfSignedCertificate(pair.public.encoded, pair.private)
        dir.mkdirs()
        keyFile.writeBytes(pair.private.encoded)
        certFile.writeBytes(cert.encoded)
        return pair.private to cert
    }

    /** Wireless debugging identifies clients by a TLS certificate; any self-signed one will do. */
    private fun selfSignedCertificate(publicKey: ByteArray, privateKey: PrivateKey): Certificate {
        val name = X500Name("CN=HiLight Plus")
        val now = System.currentTimeMillis()
        val algorithm = AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE)
        val tbs = V3TBSCertificateGenerator().apply {
            setSerialNumber(ASN1Integer(BigInteger.valueOf(now)))
            setIssuer(name)
            setSubject(name)
            setStartDate(Time(Date(now - DAY_MS)))
            setEndDate(Time(Date(now + CERT_VALIDITY_MS)))
            setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(publicKey))
            setSignature(algorithm)
        }.generateTBSCertificate()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(tbs.encoded)
            sign()
        }
        val der = DERSequence(ASN1EncodableVector().apply {
            add(tbs)
            add(algorithm)
            add(DERBitString(signature))
        }).encoded
        return CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream())
    }

    private class Manager(private val key: PrivateKey, private val cert: Certificate) : AbsAdbConnectionManager() {
        override fun getPrivateKey(): PrivateKey = key
        override fun getCertificate(): Certificate = cert
        override fun getDeviceName(): String = "HiLight Plus"
    }

    companion object {
        private const val TAG = "WirelessAdb"
        private const val LOOPBACK = "127.0.0.1"
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val CERT_VALIDITY_MS = 30 * 365 * DAY_MS

        @Volatile
        private var instance: WirelessAdb? = null

        fun get(app: Application): WirelessAdb =
            instance ?: synchronized(this) {
                instance ?: WirelessAdb(app).also { instance = it }
            }
    }
}
