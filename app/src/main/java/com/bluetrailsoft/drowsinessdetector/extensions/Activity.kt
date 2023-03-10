package com.bluetrailsoft.drowsinessdetector.extensions

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.bluetrailsoft.drowsinessdetector.R
import com.bluetrailsoft.drowsinessdetector.utils.SharedPrefs
import com.bluetrailsoft.drowsinessdetector.utils.channelDescription
import com.bluetrailsoft.drowsinessdetector.utils.channelId

@RequiresApi(Build.VERSION_CODES.O)
fun Activity.showNotification() {
    val builder: Notification.Builder
    val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val notificationChannel = NotificationChannel(channelId, channelDescription, NotificationManager.IMPORTANCE_HIGH)

    notificationChannel.enableLights(true)
    notificationChannel.lightColor = Color.RED
    notificationChannel.enableVibration(false)
    notificationManager.createNotificationChannel(notificationChannel)

    builder = Notification.Builder(applicationContext, channelId)
        .setContentTitle(applicationContext.getString(R.string.notification_title))
        .setContentText(applicationContext.getString(R.string.notification_text))
        .setSmallIcon(R.drawable.ic_alert)
        .setLargeIcon(BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_alert))
        .setContentIntent(pendingIntent)

    notificationManager.notify(1234, builder.build())
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
val NOTIFICATION_PERMISSION = listOf(Manifest.permission.POST_NOTIFICATIONS)

fun Activity.checkForNotificationAtRuntimePermissions(onSuccess: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (hasNotificationPermission()) onSuccess.invoke()
        else requestNotificationPermission { onSuccess.invoke() }
    } else onSuccess.invoke()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Activity.hasNotificationPermission() : Boolean = NOTIFICATION_PERMISSION.all {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Activity.requestNotificationPermission(onResponse: () -> Unit) {
    (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            onResponse.invoke()
        } else {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.notification_prompt))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    SharedPrefs(this).saveCheckingSettings(true)
                    redirectUserToNotificationSetting()
                }.show()
        }
    }.launch(NOTIFICATION_PERMISSION.toTypedArray())
}

@RequiresApi(Build.VERSION_CODES.O)
private fun Activity.redirectUserToNotificationSetting() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    ContextCompat.startActivity(this, intent, null)
}

val STORAGE_PERMISSION = listOf(
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.READ_EXTERNAL_STORAGE
)

fun Activity.checkForStoragePermissions(onSuccess: () -> Unit) {
    if (hasStoragePermission()) onSuccess.invoke()
    else requestStoragePermissions { onSuccess.invoke() }
}

private fun Activity.hasStoragePermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Android is 11(R) or above
        Environment.isExternalStorageManager()
    } else {
        // Android is below 11(R)
        val write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        write == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED
    }
}

private fun Activity.requestStoragePermissions(funX:() -> Unit) {
    val storageResultLauncherSdkOver30 = (this as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                funX.invoke()
            } else {
                showPromptPermission()
            }
        }
    }

    val storageResultLauncherSdkUnder30 = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            funX.invoke()
        } else {
            showPromptPermission()
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            // Android is 11(R) or above
            val intent = Intent()
            intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
            val uri = Uri.fromParts("package", this.packageName, null)
            intent.data = uri
            storageResultLauncherSdkOver30.launch(intent)
        } catch (e: Exception) {
            val intent = Intent()
            intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            storageResultLauncherSdkOver30.launch(intent)
        }
    } else {
        // Android is below 11(R)
        storageResultLauncherSdkUnder30.launch(STORAGE_PERMISSION.toTypedArray())
    }
}

private fun Activity.showPromptPermission() {
    AlertDialog.Builder(this)
        .setMessage(getString(R.string.permissions_storage_message))
        .setCancelable(false)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            redirectUserToSettings()
        }.show()
}

private fun Activity.redirectUserToSettings() {
    SharedPrefs(this).saveCheckingSettings(true)
    val intentSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intentSettings.data = getURI()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intentAccessFiles = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intentAccessFiles.data = getURI()
        startActivity(intentAccessFiles)
    } else {
        startActivity(intentSettings)
    }
}

private fun Activity.getURI() = Uri.fromParts("package", packageName, null)
