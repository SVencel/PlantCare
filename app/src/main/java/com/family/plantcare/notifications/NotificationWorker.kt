package com.family.plantcare.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.family.plantcare.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.family.plantcare.model.Plant
import kotlinx.coroutines.tasks.await
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class WateringWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val db = FirebaseFirestore.getInstance()

        val snapshot = db.collection("plants")
            .whereEqualTo("ownerId", userId)
            .get().await()

        val plants = snapshot.toObjects(Plant::class.java)
        val now = System.currentTimeMillis()

        val duePlants = plants.filter { it.nextWateringDate <= now }

        if (duePlants.isNotEmpty()) {
            showNotification(duePlants.map { it.name })
        }

        return Result.success()
    }

    private fun showNotification(plantNames: List<String>) {
        val channelId = "watering_reminders"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Plant Watering Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val contentText = if (plantNames.size == 1) {
            "Time to water ${plantNames[0]}!"
        } else {
            "You have ${plantNames.size} plants that need watering!"
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_info_details) // fallback safe icon
            .setContentTitle("🌱 PlantCare Reminder")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // ✅ Check runtime permission before notifying
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(applicationContext).notify(1001, notification)
        }
    }
}
