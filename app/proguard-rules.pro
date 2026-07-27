# Keep the Quick Settings TileService intact during R8/Proguard minification
-keep class com.dhangofa.networktoggle.NetworkTileService { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**
