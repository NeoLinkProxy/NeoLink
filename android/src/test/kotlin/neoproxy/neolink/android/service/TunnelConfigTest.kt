package neoproxy.neolink.android.service

import neoproxy.neolink.android.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelConfigTest {
    @Test
    fun toNeoLinkCfgReportsApkVersionAsClientVersion() {
        val cfg = TunnelConfig(
            remoteDomain = "nps.example.com",
            hookPort = 44801,
            connectPort = 44802,
            key = "access-key",
            localPort = 25565
        ).toNeoLinkCfg()

        assertEquals(BuildConfig.VERSION_NAME, cfg.clientVersion)
    }
}
