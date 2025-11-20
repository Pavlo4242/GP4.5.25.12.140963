package com.grindrplus.hooks

import android.annotation.SuppressLint
import com.facebook.stetho.okhttp3.StethoInterceptor // Make sure this import is here
import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@OptIn(ExperimentalStdlibApi::class)
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier")
fun sslUnpinning(param: XC_LoadPackage.LoadPackageParam) {

    // 1. Find the class first
    val okHttpBuilderClass = try {
        findClass("okhttp3.OkHttpClient\$Builder", param.classLoader)
    } catch (e: Exception) {
        Logger.e("Could not find OkHttpClient Builder: ${e.message}", )
        return
    }

    // 2. Hook ALL constructors (covers default and custom implementations)
    XposedBridge.hookAllConstructors(
        okHttpBuilderClass,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam<*>) {
                val builder = param.thisObject

                // --- PART A: SSL UNPINNING ---
                try {
                    val trustAllCerts = arrayOf<TrustManager>(
                        object : X509TrustManager {
                            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                        }
                    )

                    val sslContext = SSLContext.getInstance("TLSv1.3")
                    sslContext.init(null, trustAllCerts, SecureRandom())

                    callMethod(builder, "sslSocketFactory", sslContext.socketFactory, trustAllCerts.first() as X509TrustManager)
                    callMethod(builder, "hostnameVerifier", object : HostnameVerifier {
                        override fun verify(hostname: String?, session: SSLSession?): Boolean = true
                    })

                    // Also disable CertificatePinner
                    // (Some apps re-enable it if the builder properties are null, so we force a No-Op pinner if possible,
                    // or rely on the HookReplacement below)

                } catch (e: Throwable) {
                    Logger.e("SSL Unpinning failed on Builder init: ${e.message}",
                        )
                }

                // --- PART B: STETHO NETWORK INSPECTION ---
                try {
                    // Check if StethoInterceptor is in the classpath (It should be if compiled into APK)
                    // We invoke via callMethod to add it to the builder
                    callMethod(builder, "addNetworkInterceptor", StethoInterceptor())
                    Logger.i("Stetho Interceptor added to OkHttpClient",
                    )
                } catch (e: Throwable) {
                    // Don't crash the app if Stetho fails, just log it
                    Logger.e("Failed to add Stetho Interceptor: ${e.message}",
                    )
                }
            }
        }
    )

    // 3. Disable CertificatePinner checks (The "Nuclear" option)
    try {
        findAndHookMethod(
            "okhttp3.OkHttpClient\$Builder",
            param.classLoader,
            "certificatePinner",
            "okhttp3.CertificatePinner",
            XC_MethodReplacement.DO_NOTHING
        )
    } catch (e: Throwable) {
        Logger.e("Failed to hook certificatePinner: ${e.message}",
        )
    }

    // 4. Conscrypt Bypass (For Android 10+)
    try {
        findAndHookMethod(
            "com.android.org.conscrypt.TrustManagerImpl",
            param.classLoader,
            "verifyChain",
            List::class.java, // List<X509Certificate> untrustedChain
            List::class.java, // List<TrustAnchor> trustAnchorChain
            String::class.java, // String host
            Boolean::class.java, // boolean clientAuth
            ByteArray::class.java, // byte[] ocspData
            ByteArray::class.java, // byte[] tlsSctData
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    // Return the untrusted chain immediately, bypassing verification
                    param.result = param.args[0]
                }
            }
        )
    } catch (e: Throwable) {
        Logger.e("Failed to hook Conscrypt: ${e.message}",)
    }
}