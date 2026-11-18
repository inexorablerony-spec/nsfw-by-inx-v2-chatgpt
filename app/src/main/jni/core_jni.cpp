#include <jni.h>
#include <dlfcn.h>
#include <cstdint>
#include <mutex>

static std::mutex g_mutex;
static void* g_handle = nullptr;
static int32_t (*g_start)(uint16_t) = nullptr;
static int32_t (*g_stop)(void) = nullptr;
static int32_t (*g_is_running)(void) = nullptr;

extern "C" {

JNIEXPORT jint JNICALL
Java_io_github_aedev_flow_core_CoreNative_nativeLoad(
    JNIEnv* env, jobject, jstring path) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_handle) return 0;
    
    const char* c_path = env->GetStringUTFChars(path, nullptr);
    g_handle = dlopen(c_path, RTLD_NOW | RTLD_LOCAL);
    env->ReleaseStringUTFChars(path, c_path);
    
    if (!g_handle) return -1;
    
    g_start = (int32_t(*)(uint16_t))dlsym(g_handle, "start_core_service");
    g_stop = (int32_t(*)(void))dlsym(g_handle, "stop_core_service");
    g_is_running = (int32_t(*)(void))dlsym(g_handle, "is_core_service_running");
    
    if (!g_start || !g_stop || !g_is_running) {
        dlclose(g_handle);
        g_handle = nullptr;
        g_start = nullptr;
        g_stop = nullptr;
        g_is_running = nullptr;
        return -2;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_io_github_aedev_flow_core_CoreNative_nativeStart(
    JNIEnv*, jobject, jint port) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_start) return -1;
    return g_start((uint16_t)port);
}

JNIEXPORT jint JNICALL
Java_io_github_aedev_flow_core_CoreNative_nativeStop(
    JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_stop) return -1;
    return g_stop();
}

JNIEXPORT jint JNICALL
Java_io_github_aedev_flow_core_CoreNative_nativeIsRunning(
    JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_is_running) return 0;
    return g_is_running();
}

JNIEXPORT jboolean JNICALL
Java_io_github_aedev_flow_core_CoreNative_nativeIsLoaded(
    JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_handle != nullptr;
}

} // extern "C"
