package com.autoreply.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 부팅 완료 시 별도 작업 없음 (CallReceiver는 매니페스트로 등록되어 있어 자동 동작)
    }
}
