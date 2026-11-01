package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 시동(부팅) 때 수집 서비스를 켠다. 앱을 한 번도 열지 않으면 Android가 이 신호를 보내지 않는다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            DriveService.start(context)
        }
    }
}
