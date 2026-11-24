package com.example.wear.data

import com.example.shared.WearSyncCodec
import com.example.shared.WearSyncContract
import com.example.wear.liveupdates.WatchPomodoroForegroundService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class WatchWearListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val coordinator = WatchSyncCoordinator.getInstance(applicationContext)
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearSyncContract.PATH_SNAPSHOT) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val raw = map.getString(WearSyncContract.KEY_SNAPSHOT_JSON)
                val snapshot = WearSyncCodec.decodeSnapshot(raw)
                coordinator.onPhoneSnapshotReceived(snapshot)

                val running = snapshot.workItems.firstOrNull { it.status == WatchWorkStatus.IN_PROGRESS.name }
                if (running != null) {
                    val projName = snapshot.projects.firstOrNull { it.id == running.projectId }?.name.orEmpty()
                    WatchPomodoroForegroundService.syncActiveSession(applicationContext, running, projName)
                } else {
                    WatchPomodoroForegroundService.stop(applicationContext)
                }
            }
        }
    }
}
