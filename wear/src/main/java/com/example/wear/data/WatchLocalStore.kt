package com.example.wear.data

import android.content.Context
import com.example.shared.WearCommand
import com.example.shared.WearSyncCodec
import com.example.shared.WearSyncSnapshot
import org.json.JSONArray

class WatchLocalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun readSnapshot(): WearSyncSnapshot {
        val raw = prefs.getString(KEY_SNAPSHOT_JSON, null)
        return WearSyncCodec.decodeSnapshot(raw)
    }

    @Synchronized
    fun saveSnapshot(snapshot: WearSyncSnapshot) {
        prefs.edit()
            .putString(KEY_SNAPSHOT_JSON, WearSyncCodec.encodeSnapshot(snapshot))
            .apply()
    }

    @Synchronized
    fun readPendingCommands(): List<WearCommand> {
        val raw = prefs.getString(KEY_QUEUE_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val cmd = WearSyncCodec.decodeCommand(arr.optString(i))
                    if (cmd != null) add(cmd)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun enqueueCommand(command: WearCommand) {
        val current = readPendingCommands().filterNot { it.commandId == command.commandId } + command
        saveQueue(current)
    }

    @Synchronized
    fun removeCommands(commandIds: Set<String>) {
        if (commandIds.isEmpty()) return
        val remaining = readPendingCommands().filterNot { it.commandId in commandIds }
        saveQueue(remaining)
    }

    @Synchronized
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun saveQueue(list: List<WearCommand>) {
        val arr = JSONArray()
        list.forEach { arr.put(WearSyncCodec.encodeCommand(it)) }
        prefs.edit().putString(KEY_QUEUE_JSON, arr.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "taskpulse_wear_store"
        const val KEY_SNAPSHOT_JSON = "snapshot_json"
        const val KEY_QUEUE_JSON = "pending_commands_json"
    }
}
