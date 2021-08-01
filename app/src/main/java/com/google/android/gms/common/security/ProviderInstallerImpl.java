/*
 * SPDX-FileCopyrightText: 2020, microG Project Team
 * SPDX-FileContributor: Modified by nnuudev
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.common.security;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.conscrypt.Conscrypt;
import org.conscrypt.NativeCrypto;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

@Keep
public class ProviderInstallerImpl {
    private static final String TAG = "FakeGMSProvider";
    private static final List<String> DISABLED = Collections.unmodifiableList(Arrays.asList("com.bankid.bus"));

    private static final Object lock = new Object();
    private static Provider provider;

    private static String getRealSelfPackageName(Context context) {
        String packageName = PackageUtils.packageFromProcessId(context, Process.myPid());
        if (packageName != null && packageName.contains(".")) return packageName;
        try {
            Method getBasePackageName = Context.class.getDeclaredMethod("getBasePackageName");
            packageName = (String) getBasePackageName.invoke(context);
            if (packageName != null) return packageName;
        } catch (Exception e) {

        }
        if (Build.VERSION.SDK_INT >= 29) {
            return context.getOpPackageName();
        }
        Context applicationContext = context.getApplicationContext();
        if (applicationContext != null) {
            return applicationContext.getPackageName();
        }
        return context.getPackageName();
    }

    @Keep
    public static void insertProvider(Context context) {
        String packageName = getRealSelfPackageName(context);
        try {
            if (DISABLED.contains(packageName)) {
                Log.d(TAG, "Package " + packageName + " is excluded from usage of provider installer");
                return;
            }
            if (Security.getProvider(PROVIDER_NAME) != null) {
                Log.d(TAG, "Provider already inserted in " + packageName);
                return;
            }

            synchronized (lock) {
                initProvider(context, packageName);

                if (provider == null) {
                    Log.w(TAG, "Failed to initialize Conscrypt");
                    return;
                }

                int res = Security.insertProviderAt(provider, 1);
                if (res == 1) {
                    Security.setProperty("ssl.SocketFactory.provider", "org.conscrypt.OpenSSLSocketFactoryImpl");
                    Security.setProperty("ssl.ServerSocketFactory.provider", "org.conscrypt.OpenSSLServerSocketFactoryImpl");

                    SSLContext sslContext = SSLContext.getInstance("Default");
                    SSLContext.setDefault(sslContext);
                    HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

                    Log.d(TAG, "Installed default security provider " + PROVIDER_NAME);
                } else {
                    throw new SecurityException("Failed to install security provider " + PROVIDER_NAME + ", result: " + res);
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, e);
        }
    }

    public void reportRequestStats(Context context, long a, long b) {
        // Ignore stats
    }

    private static void initProvider(Context context, String packageName) {
        Log.d(TAG, "Initializing provider for " + packageName);

        try {
            loadConscryptDirect(context, packageName);
            provider = Conscrypt.newProviderBuilder().setName(PROVIDER_NAME).defaultTlsProtocol("TLSv1.2").build();
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }

    private static void loadConscryptDirect(Context context, String packageName) throws Exception {
        String path = "/system/lib/libconscrypt_jni.so";
        Log.d(TAG, "Loading libconscrypt_jni from " + path);
        System.load(path);

        Class<NativeCrypto> clazz = NativeCrypto.class;

        Field loadError = clazz.getDeclaredField("loadError");
        loadError.setAccessible(true);
        loadError.set(null, null);

        Method clinit = clazz.getDeclaredMethod("clinit");
        clinit.setAccessible(true);

        Method get_cipher_names = clazz.getDeclaredMethod("get_cipher_names", String.class);
        get_cipher_names.setAccessible(true);

        Method cipherSuiteToJava = clazz.getDeclaredMethod("cipherSuiteToJava", String.class);
        cipherSuiteToJava.setAccessible(true);

        Method EVP_has_aes_hardware = clazz.getDeclaredMethod("EVP_has_aes_hardware");
        EVP_has_aes_hardware.setAccessible(true);

        Field f = clazz.getDeclaredField("SUPPORTED_TLS_1_2_CIPHER_SUITES_SET");
        f.setAccessible(true);

        Set<String> SUPPORTED_TLS_1_2_CIPHER_SUITES_SET = (Set<String>) f.get(null);
        f = clazz.getDeclaredField("SUPPORTED_LEGACY_CIPHER_SUITES_SET");
        f.setAccessible(true);

        Set<String> SUPPORTED_LEGACY_CIPHER_SUITES_SET = (Set<String>) f.get(null);
        f = clazz.getDeclaredField("SUPPORTED_TLS_1_2_CIPHER_SUITES");
        f.setAccessible(true);

        try {
            clinit.invoke(null);

            String[] allCipherSuites = (String[]) get_cipher_names.invoke(null, "ALL:!DHE");
            int size = allCipherSuites.length;

            String[] SUPPORTED_TLS_1_2_CIPHER_SUITES = new String[size / 2 + 2];
            for (int i = 0; i < size; i += 2) {
                String cipherSuite = (String) cipherSuiteToJava.invoke(null, allCipherSuites[i]);

                SUPPORTED_TLS_1_2_CIPHER_SUITES[i / 2] = cipherSuite;
                SUPPORTED_TLS_1_2_CIPHER_SUITES_SET.add(cipherSuite);

                SUPPORTED_LEGACY_CIPHER_SUITES_SET.add(allCipherSuites[i + 1]);
            }
            SUPPORTED_TLS_1_2_CIPHER_SUITES[size / 2] = "TLS_EMPTY_RENEGOTIATION_INFO_SCSV";
            SUPPORTED_TLS_1_2_CIPHER_SUITES[size / 2 + 1] = "TLS_FALLBACK_SCSV";
            f.set(null, SUPPORTED_TLS_1_2_CIPHER_SUITES);

            f = clazz.getDeclaredField("HAS_AES_HARDWARE");
            f.setAccessible(true);
            f.set(null, (int) EVP_has_aes_hardware.invoke(null) == 1);

        } catch (InvocationTargetException inner) {
            if (inner.getTargetException() instanceof UnsatisfiedLinkError) {
                loadError.set(null, inner.getTargetException());
            }
        }
    }

    public static final String PROVIDER_NAME = "GmsCore_OpenSSL";

    private static class PackageUtils {
        @Nullable
        @Deprecated
        public static String packageFromProcessId(Context context, int pid) {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return null;
            if (pid <= 0) return null;
            List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = manager.getRunningAppProcesses();
            if (runningAppProcesses != null) {
                for (ActivityManager.RunningAppProcessInfo processInfo : runningAppProcesses) {
                    if (processInfo.pid == pid && processInfo.pkgList.length == 1) {
                        return processInfo.pkgList[0];
                    }
                }
            }
            return null;
        }
    }
}
