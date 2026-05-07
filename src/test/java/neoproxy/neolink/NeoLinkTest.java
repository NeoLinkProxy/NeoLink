package neoproxy.neolink;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("NeoLinkTest")
class NeoLinkTest {

    @AfterEach
    void tearDown() throws Exception {
        NeoLink.resetExitHandler();
        Field autoStartField = NeoLink.class.getDeclaredField("shouldAutoStartInGUI");
        autoStartField.setAccessible(true);
        autoStartField.setBoolean(null, false);
    }

    @Test
    @DisplayName("常量保持稳定 / constants remain stable")
    void constantsRemainStable() {
        assertEquals("NeoLink-", NeoLink.CLIENT_FILE_PREFIX);
        assertEquals("0.0.1", NeoLink.TEST_UPDATE_VERSION);
        assertEquals(System.getProperty("user.dir"), NeoLink.CURRENT_DIR_PATH);
        assertEquals(-1, NeoLink.INVALID_LOCAL_PORT);
        assertTrue(NeoLink.ASCII_LOGO.contains("_"));
    }

    @Test
    @DisplayName("GUI 自动启动标记可读 / auto-start flag is readable")
    void autoStartFlagIsReadable() throws Exception {
        Field autoStartField = NeoLink.class.getDeclaredField("shouldAutoStartInGUI");
        autoStartField.setAccessible(true);

        autoStartField.setBoolean(null, false);
        assertFalse(NeoLink.shouldAutoStart());

        autoStartField.setBoolean(null, true);
        assertTrue(NeoLink.shouldAutoStart());
    }

    @Test
    @DisplayName("退出处理器可替换 / exit handler is swappable")
    void exitHandlerIsSwappable() {
        AtomicInteger exitCode = new AtomicInteger(Integer.MIN_VALUE);

        NeoLink.setExitHandler(exitCode::set);
        NeoLink.requestExit(7);

        assertEquals(7, exitCode.get());
    }
}
