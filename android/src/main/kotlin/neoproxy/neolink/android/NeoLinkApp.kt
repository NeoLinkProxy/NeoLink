package neoproxy.neolink.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import neoproxy.neolink.config.ConfigOperator

/**
 * Application 入口，负责全局初始化。
 * 创建通知渠道供 TunnelService 前台通知使用。
 */
class NeoLinkApp : Application() {

    companion object {
        const val CHANNEL_TUNNEL = "tunnel_channel"
    }

    override fun onCreate() {
        super.onCreate()
        ConfigOperator.setWorkingDirectoryProvider { filesDir.toPath() }
        ConfigOperator.initEnvironment()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            CHANNEL_TUNNEL,
            "隧道服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "NeoLink 隧道连接状态"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
