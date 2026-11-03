package io.github.henry753951.airplayremote.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.github.henry753951.airplayremote.Prefs
import io.github.henry753951.airplayremote.audiooutput.HomePodOutputService
import io.github.henry753951.airplayremote.audiooutput.SpeakerOutputState
import io.github.henry753951.airplayremote.get
import io.github.henry753951.airplayremote.headphones.HeadphonesController
import io.github.henry753951.airplayremote.headphones.HeadphonesStatus
import io.github.henry753951.airplayremote.observe
import io.github.henry753951.airplayremote.remote.RemoteState
import io.github.henry753951.airplayremote.runtime.AirPlayService
import io.github.henry753951.airplayremote.runtime.AirPlayService.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the Telik cards on the Home screen show. Plain values, so the screen needs no service. */
data class TelikStatus(
    val server: ServerState = ServerState.STOPPED,
    val serverName: String = "",
    val connections: Int = 0,
    val mirroring: Boolean = false,
    val audioOnly: Boolean = false,
    val track: String = "",
    val remoteEnabled: Boolean = true,
    val remote: String = "",
    val remoteConnected: Boolean = false,
    val headphones: HeadphonesStatus = HeadphonesStatus.IDLE,
    val speaker: SpeakerOutputState = SpeakerOutputState(),
)

private data class Session(
    val server: ServerState = ServerState.STOPPED,
    val connections: Int = 0,
    val mirroring: Boolean = false,
    val audioOnly: Boolean = false,
    val track: String = "",
)

/**
 * Reads the receiver state for the Home screen. It binds to the AirPlay service only when the service
 * runs already (no BIND_AUTO_CREATE), so the Home screen never starts or changes the receiver.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelikWatcher(private val context: Context, scope: CoroutineScope) {
    private val service = MutableStateFlow<AirPlayService?>(null)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service.value = (binder as? AirPlayService.LocalBinder)?.service
        }
        override fun onServiceDisconnected(name: ComponentName?) { service.value = null }
    }

    private val session: Flow<Session> = service.flatMapLatest { svc ->
        if (svc == null) flowOf(Session()) else combine(
            svc.serverState, svc.connectionCount, svc.mirroringActive, svc.audioOnly, svc.trackInfo,
        ) { server, connections, mirroring, audioOnly, track ->
            Session(server, connections, mirroring, audioOnly, track.title)
        }
    }

    private val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        .observe { this[Prefs.SERVER_NAME] to this[Prefs.REMOTE_ENABLED] }

    private val remote = combine(RemoteState.status, RemoteState.connected) { s, c -> s to c }
    private val accessories = combine(HeadphonesController.status, HomePodOutputService.state) { h, s -> h to s }

    val status: StateFlow<TelikStatus> = combine(session, prefs, remote, accessories) { s, p, r, a ->
        TelikStatus(
            server = s.server, serverName = p.first, connections = s.connections,
            mirroring = s.mirroring, audioOnly = s.audioOnly, track = s.track,
            remoteEnabled = p.second, remote = r.first, remoteConnected = r.second,
            headphones = a.first, speaker = a.second,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), TelikStatus())

    fun bind() {
        context.bindService(Intent(context, AirPlayService::class.java), connection, 0)
    }

    fun unbind() {
        runCatching { context.unbindService(connection) }
        service.value = null
    }

    /** Connect or disconnect the headphones. The controller normally runs already; start is a no-op then. */
    fun toggleHeadphones() {
        HeadphonesController.start(context.applicationContext)
        HeadphonesController.toggleConnection()
    }
}
