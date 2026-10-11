package cn.com.omnimind.nativeui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeQrCodeTest {
    @Test fun jsonPayloadsCarryTheirFieldsAndCheckTheType() {
        assertEquals(BridgeQrCode("ws://10.0.0.2:17321/codex", "t1", "/repo"),
            parseBridgeQrCode("""{"type":"omnibot.codex_bridge","bridgeUrl":"ws://10.0.0.2:17321/codex","token":"t1","cwd":"/repo"}"""))
        assertEquals("ws://h/codex", parseBridgeQrCode("""{"wsUrl":"ws://h/codex","bridgeToken":"x"}""")?.bridgeUrl)
        assertNull(parseBridgeQrCode("""{"type":"wifi","url":"ws://h"}"""))
        assertNull(parseBridgeQrCode("""{"token":"only"}"""))
    }

    @Test fun omnibotLinksReadTheirQuery() {
        assertEquals(BridgeQrCode("ws://h:17321/codex", "a b", "/w"),
            parseBridgeQrCode("omnibot://codex-bridge?bridgeUrl=ws%3A%2F%2Fh%3A17321%2Fcodex&token=a%20b&cwd=%2Fw"))
        assertNull(parseBridgeQrCode("omnibot://codex-bridge?token=x"))
    }

    @Test fun bareBridgeUrlsDropTheQuickConnectQuery() {
        assertEquals(BridgeQrCode("ws://192.168.1.4:17321/codex?v=2", "tok", "/src"),
            parseBridgeQrCode("ws://192.168.1.4:17321/codex?token=tok&v=2&cwd=/src"))
        // A health URL points the stored Bridge at /codex.
        assertEquals("http://pc.local:17321/codex", parseBridgeQrCode("http://pc.local:17321/health?token=t")?.bridgeUrl)
        assertEquals("https://h/codex", parseBridgeQrCode("https://h/codex")?.bridgeUrl)
    }

    @Test fun otherCodesAreRejected() {
        assertNull(parseBridgeQrCode(""))
        assertNull(parseBridgeQrCode("https://example.com/menu"))
        assertNull(parseBridgeQrCode("WIFI:S:home;P:secret;;"))
        assertNull(parseBridgeQrCode("just text"))
    }
}
