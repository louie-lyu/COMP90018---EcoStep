package com.ecostep.app.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ecostep.app.algorithm.MissionTrigger
import com.ecostep.app.data.model.Mission

/**
 * Inexact AlarmManager reminders (no exact-alarm permission). Each mission has one alarm
 * identified by its ID, so scheduling again replaces the previous one. Scheduled IDs are
 * remembered on the device so sign-out can cancel alarms set by an earlier app run.
 * Alarms do not survive a reboot; they are restored the next time the app syncs.
 */
class AndroidMissionReminderScheduler(
    context: Context,
) : MissionReminderScheduler {

    private val context = context.applicationContext
    private val alarmManager = this.context.getSystemService(AlarmManager::class.java)
    private val store = this.context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    override fun schedule(mission: Mission, trigger: MissionTrigger, title: String, message: String) {
        val intent = reminderIntent(mission.missionId)
            .putExtra(MissionReminderReceiver.EXTRA_TITLE, title)
            .putExtra(MissionReminderReceiver.EXTRA_MESSAGE, message)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            mission.missionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.notificationTimeMillis, pendingIntent)
        updateIds { it + mission.missionId }
    }

    override fun cancel(missionId: String) {
        PendingIntent.getBroadcast(
            context,
            missionId.hashCode(),
            reminderIntent(missionId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { pendingIntent ->
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        updateIds { it - missionId }
    }

    override fun cancelAll() {
        scheduledMissionIds().forEach(::cancel)
    }

    override fun scheduledMissionIds(): Set<String> =
        store.getStringSet(KEY_IDS, emptySet()).orEmpty().toSet()

    /** The data URI makes each mission's PendingIntent distinct and cancellable. */
    private fun reminderIntent(missionId: String): Intent =
        Intent(context, MissionReminderReceiver::class.java)
            .setData(Uri.Builder().scheme("ecostep").authority("mission").appendPath(missionId).build())

    private fun updateIds(change: (Set<String>) -> Set<String>) {
        store.edit().putStringSet(KEY_IDS, change(scheduledMissionIds())).apply()
    }

    private companion object {
        const val STORE_NAME = "mission_reminders"
        const val KEY_IDS = "scheduled_mission_ids"
    }
}
