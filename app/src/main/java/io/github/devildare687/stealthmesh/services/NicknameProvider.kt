package io.github.devildare687.stealthmesh.services

import android.content.Context
import io.github.devildare687.stealthmesh.ui.DataManager

/**
 * Provides current user's nickname for announcements and leave messages.
 * If no nickname saved, falls back to the provided peerID.
 */
object NicknameProvider {
    fun getNickname(context: Context, myPeerID: String): String {
        return try {
            val dm = DataManager(context.applicationContext)
            val nick = dm.loadNickname()
            if (nick.isNullOrBlank()) myPeerID else nick
        } catch (_: Exception) {
            myPeerID
        }
    }
}

