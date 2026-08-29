package dev.smto.driveassistant.car

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

/**
 * Tracks whether the phone is currently projecting to an Android Auto head unit,
 * via the official `androidx.car.app` Connection API. Read [projecting] from any
 * thread; it is updated on the main thread by the observer.
 */
class CarConnectionState(context: Context) {

    @Volatile
    var projecting: Boolean = false
        private set

    private val type = CarConnection(context.applicationContext).type
    private val observer = Observer<Int> { state ->
        projecting = state == CarConnection.CONNECTION_TYPE_PROJECTION
    }

    /** Must be called on the main thread (e.g. from Application.onCreate). */
    fun start() = type.observeForever(observer)
}
