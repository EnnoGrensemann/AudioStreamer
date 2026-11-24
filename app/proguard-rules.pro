# Keep OkHttp / Kotlin / Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep JNI callback classes and methods called from native usb_audio_iso.c
-keep class com.vinylcast.audio.UsbIsoNative { *; }
-keep class com.vinylcast.audio.UsbIsoNative$PcmCallback {
    public void onPcmData(byte[], int);
}
-keepclassmembers class * implements com.vinylcast.audio.UsbIsoNative$PcmCallback {
    public void onPcmData(byte[], int);
}
