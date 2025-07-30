package com.family.plantcare.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.family.plantcare.model.Plant
import com.family.plantcare.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class WateringWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val db = FirebaseFirestore.getInstance()

        try {
            // ✅ Load user to get household memberships
            val userSnap = db.collection("users").document(userId).get().await()
            val user = userSnap.toObject(User::class.java)
            val householdIds = user?.households ?: emptyList()

            // ✅ Query private plants
            val privateSnap = db.collection("plants")
                .whereEqualTo("ownerId", userId)
                .get().await()
            val privatePlants = privateSnap.toObjects(Plant::class.java)

            // ✅ Query household plants (if any)
            val householdPlants = mutableListOf<Plant>()
            if (householdIds.isNotEmpty()) {
                val chunks = householdIds.chunked(10) // Firestore whereIn supports max 10
                for (chunk in chunks) {
                    val snap = db.collection("plants")
                        .whereIn("householdId", chunk)
                        .get().await()
                    householdPlants.addAll(snap.toObjects(Plant::class.java))
                }
            }

            // ✅ Combine all plants
            val allPlants = privatePlants + householdPlants
            val now = System.currentTimeMillis()
            val duePlants = allPlants.filter { it.nextWateringDate <= now }

            if (duePlants.isNotEmpty()) {
                showNotification(duePlants.map { it.name })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Result.success()
    }

    private fun showNotification(plantNames: List<String>) {
        val channelId = "watering_reminders"
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("🌱 PlantCare Reminder")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // ✅ Only notify if POST_NOTIFICATIONS is granted
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(applicationContext).notify(1001, notification)
        }
    }
}
