package com.example.wearsync

import com.example.shared.WearSyncCodec
import com.example.shared.WearSyncContract
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PhoneWearListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearSyncContract.PATH_REQUEST_SYNC -> {
                serviceScope.launch {
                    PhoneWearSyncManager.publishFromRepository(applicationContext)
                }
            }
            WearSyncContract.PATH_COMMAND -> {
                val payload = messageEvent.data.toString(Charsets.UTF_8)
                val cmd = WearSyncCodec.decodeCommand(payload) ?: return
                serviceScope.launch {
                    PhoneWearSyncManager.handleIncomingCommand(applicationContext, cmd)
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
