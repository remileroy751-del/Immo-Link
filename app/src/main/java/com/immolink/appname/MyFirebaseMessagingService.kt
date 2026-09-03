package com.immolink.appname

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).update("fcmToken", token)
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val channelId = "immolink_notifications"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(NotificationChannel(channelId, "ImmoLink", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId).setSmallIcon(R.drawable.ic_notification).setContentTitle(message.notification?.title ?: "ImmoLink").setContentText(message.notification?.body ?: "Nouvelle notification").setAutoCancel(true).setContentIntent(pending).build()
        manager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }
}
