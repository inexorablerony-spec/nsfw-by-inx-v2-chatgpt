package io.github.aedev.flow.network

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * WARP Manager for Cloudflare WARP integration.
 * 
 * IMPORTANT: Cloudflare does not provide an embeddable SDK for WARP.
 * The only supported way to use WARP is through the official 1.1.1.1 app.
 * 
 * This manager provides:
 * - Check if the official 1.1.1.1 app is installed
 * - Link to install the official 1.1.1.1 app
 * - Open the 1.1.1.1 app if installed
 * - No fake proxy or connection status
 */
object WarpManager {
    private const val WARP_PACKAGE = "com.cloudflare.onedotonedotonedotone"
    private const val WARP_PLAY_STORE = "https://play.google.com/store/apps/details?id=$WARP_PACKAGE"
    
    /**
     * Check if the official 1.1.1.1 app is installed.
     * Requires the package to be in <queries> for Android 11+.
     */
    fun isWarpAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(WARP_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Open the Play Store to install the 1.1.1.1 app.
     * Returns true if successful, false if Play Store cannot be opened.
     */
    fun openPlayStore(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WARP_PLAY_STORE))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Open the 1.1.1.1 app if installed.
     * Returns true if successful, false if no launch intent.
     */
    fun openWarpApp(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(WARP_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
