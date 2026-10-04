package com.ecostep.app.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ecostep.app.EcoStepApp
import com.ecostep.app.MainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Shows a mission reminder, then schedules the mission's next one. */
class MissionReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val missionId = intent.data?.lastPathSegment ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        showNotification(context, missionId, title, message)

        val app = context.applicationContext as? EcoStepApp ?: return
        val pending = goAsync()
        app.appContainer.applicationScope.launch {
            try {
                withTimeoutOrNull(RESYNC_TIMEOUT_MILLIS) {
                    app.appContainer.userPreferenceCoordinator.resync()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context, missionId: String, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(missionId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    companion object {
        const val EXTRA_TITLE = "com.ecostep.app.reminder.TITLE"
        const val EXTRA_MESSAGE = "com.ecostep.app.reminder.MESSAGE"

        /** Separate from the journey-tracking channel so users can mute each independently. */
        const val CHANNEL_ID = "mission_reminders"
        private const val RESYNC_TIMEOUT_MILLIS = 8_000L

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Mission reminders", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
