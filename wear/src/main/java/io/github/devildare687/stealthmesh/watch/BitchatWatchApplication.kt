package io.github.devildare687.stealthmesh.watch

import android.app.Application
import io.github.devildare687.stealthmesh.mesh.PowerManager
import io.github.devildare687.stealthmesh.watch.notification.WearNotificationCoordinator
import io.github.devildare687.stealthmesh.watch.ui.WearPeerIdentityState

class BitchatWatchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PowerManager.getInstance(applicationContext)
        WearNotificationCoordinator.getInstance(applicationContext)
        WearPeerIdentityState.initialize(applicationContext)
    }
}
