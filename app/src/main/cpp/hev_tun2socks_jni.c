#include <jni.h>
#include <stddef.h>
#include <string.h>
#include <android/log.h>

#include "hev-main.h"
#include "jni_package_config.h"

#define TAG "HevTun2SocksJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static jint
native_start (JNIEnv *env, jobject thiz, jstring config_str, jint tun_fd)
{
    const char *config = (*env)->GetStringUTFChars (env, config_str, NULL);
    jsize config_len = (*env)->GetStringUTFLength (env, config_str);
    int res;

    LOGI ("Starting native tunnel with config length %d and FD %d", (int)config_len, tun_fd);
    res = hev_socks5_tunnel_main_from_str ((const unsigned char *)config, (unsigned int)config_len, tun_fd);

    (*env)->ReleaseStringUTFChars (env, config_str, config);
    return (jint)res;
}

static void
native_stop (JNIEnv *env, jobject thiz)
{
    LOGI ("Stopping native tunnel");
    hev_socks5_tunnel_quit ();
}

static jlongArray
native_get_stats (JNIEnv *env, jobject thiz)
{
    size_t tx_packets, tx_bytes, rx_packets, rx_bytes;
    jlong stats[4];
    jlongArray res;

    hev_socks5_tunnel_stats (&tx_packets, &tx_bytes, &rx_packets, &rx_bytes);

    stats[0] = (jlong)tx_packets;
    stats[1] = (jlong)tx_bytes;
    stats[2] = (jlong)rx_packets;
    stats[3] = (jlong)rx_bytes;

    res = (*env)->NewLongArray (env, 4);
    if (res)
        (*env)->SetLongArrayRegion (env, res, 0, 4, stats);

    return res;
}

static jint
native_get_version (JNIEnv *env, jobject thiz)
{
    /* Return a constant for smoke test validation */
    return 2171;
}

static JNINativeMethod g_methods[] = {
    { "nativeStart", "(Ljava/lang/String;I)I", (void *)native_start },
    { "nativeStop", "()V", (void *)native_stop },
    { "nativeGetStats", "()[J", (void *)native_get_stats },
    { "nativeGetVersion", "()I", (void *)native_get_version },
};

JNIEXPORT jint JNI_OnLoad (JavaVM *vm, void *reserved)
{
    JNIEnv *env;
    jclass cls;

    if ((*vm)->GetEnv (vm, (void **)&env, JNI_VERSION_1_4) != JNI_OK)
        return JNI_ERR;

    cls = (*env)->FindClass (env, HEV_TUN2SOCKS_JNI_CLASS_PATH);
    if (!cls)
        return JNI_ERR;

    if ((*env)->RegisterNatives (env, cls, g_methods, sizeof (g_methods) / sizeof (g_methods[0])) < 0)
        return JNI_ERR;

    return JNI_VERSION_1_4;
}
