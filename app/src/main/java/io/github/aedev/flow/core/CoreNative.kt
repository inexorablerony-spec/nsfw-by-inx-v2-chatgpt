package io.github.aedev.flow.core

object CoreNative {
    init {
        System.loadLibrary("core_jni")
    }

    external fun nativeLoad(path: String): Int
    external fun nativeStart(port: Int): Int
    external fun nativeStop(): Int
    external fun nativeIsRunning(): Int
    external fun nativeIsLoaded(): Boolean
}
