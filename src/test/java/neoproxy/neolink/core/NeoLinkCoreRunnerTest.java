package neoproxy.neolink.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NeoLinkCoreRunner 测试类
 *
 * 测试范围：
 * 1. 停止请求机制
 * 2. 回调设置
 * 3. shouldStop 状态管理
 */
@DisplayName("NeoLinkCoreRunner 核心运行器测试")
class NeoLinkCoreRunnerTest {

    private boolean originalEnableAutoReconnect;

    @BeforeEach
    void setUp() throws Exception {
        originalEnableAutoReconnect = NeoLink.enableAutoReconnect;

        Field shouldStopField = NeoLinkCoreRunner.class.getDeclaredField("shouldStop");
        shouldStopField.setAccessible(true);
        shouldStopField.set(null, false);
    }

    @AfterEach
    void tearDown() throws Exception {
        NeoLink.enableAutoReconnect = originalEnableAutoReconnect;

        Field shouldStopField = NeoLinkCoreRunner.class.getDeclaredField("shouldStop");
        shouldStopField.setAccessible(true);
        shouldStopField.set(null, false);
    }

    @Test
    @DisplayName("requestStop 应设置 shouldStop 为 true")
    void testRequestStopSetsShouldStopTrue() throws Exception {
        Field shouldStopField = NeoLinkCoreRunner.class.getDeclaredField("shouldStop");
        shouldStopField.setAccessible(true);

        assertFalse(shouldStopField.getBoolean(null));

        NeoLinkCoreRunner.requestStop();

        assertTrue(shouldStopField.getBoolean(null));
    }

    @Test
    @DisplayName("setStopCallback 应正确存储回调")
    void testSetStopCallback() throws Exception {
        boolean[] callbackInvoked = {false};
        NeoLinkCoreRunner.StopCallback callback = () -> callbackInvoked[0] = true;

        NeoLinkCoreRunner.setStopCallback(callback);

        Field callbackField = NeoLinkCoreRunner.class.getDeclaredField("stopCallback");
        callbackField.setAccessible(true);
        NeoLinkCoreRunner.StopCallback storedCallback = (NeoLinkCoreRunner.StopCallback) callbackField.get(null);

        assertNotNull(storedCallback);
        storedCallback.onStop();
        assertTrue(callbackInvoked[0]);
    }

    @Test
    @DisplayName("多次调用 requestStop 应安全")
    void testMultipleRequestStopCalls() {
        assertDoesNotThrow(() -> {
            NeoLinkCoreRunner.requestStop();
            NeoLinkCoreRunner.requestStop();
            NeoLinkCoreRunner.requestStop();
        });
    }

    @Test
    @DisplayName("setStopCallback 为 null 应安全")
    void testSetStopCallbackNull() {
        assertDoesNotThrow(() -> NeoLinkCoreRunner.setStopCallback(null));
    }

    @Test
    @DisplayName("StopCallback 接口应定义 onStop 方法")
    void testStopCallbackInterface() throws Exception {
        Class<?> callbackInterface = Class.forName("neoproxy.neolink.core.NeoLinkCoreRunner$StopCallback");
        assertTrue(callbackInterface.isInterface());
        assertNotNull(callbackInterface.getDeclaredMethod("onStop"));
    }
}
