package neoproxy.neolink.network.threads;

import neoproxy.neolink.core.NeoLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CheckAliveThread 测试类
 *
 * 测试范围：
 * 1. 单例模式
 * 2. 线程启动/停止
 * 3. 常量验证
 */
@DisplayName("CheckAliveThread 心跳检测线程测试")
class CheckAliveThreadTest {

    private int originalHeartbeatDelay;

    @BeforeEach
    void setUp() throws Exception {
        originalHeartbeatDelay = CheckAliveThread.HEARTBEAT_PACKET_DELAY;

        Field instanceField = CheckAliveThread.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @AfterEach
    void tearDown() {
        CheckAliveThread.HEARTBEAT_PACKET_DELAY = originalHeartbeatDelay;
        CheckAliveThread.stopThread();
    }

    @Test
    @DisplayName("HEARTBEAT_PACKET 应为 PING")
    void testHeartbeatPacketConstant() throws Exception {
        Field field = CheckAliveThread.class.getDeclaredField("HEARTBEAT_PACKET");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertEquals("PING", value);
    }

    @Test
    @DisplayName("MAX_CONSECUTIVE_FAILURES 应为 5")
    void testMaxConsecutiveFailuresConstant() throws Exception {
        Field field = CheckAliveThread.class.getDeclaredField("MAX_CONSECUTIVE_FAILURES");
        field.setAccessible(true);
        int value = field.getInt(null);
        assertEquals(5, value);
    }

    @Test
    @DisplayName("HEARTBEAT_PACKET_DELAY 默认应为 1000")
    void testHeartbeatPacketDelayDefault() {
        assertEquals(1000, CheckAliveThread.HEARTBEAT_PACKET_DELAY);
    }

    @Test
    @DisplayName("HEARTBEAT_PACKET_DELAY 应可修改")
    void testHeartbeatPacketDelayModifiable() {
        CheckAliveThread.HEARTBEAT_PACKET_DELAY = 2000;
        assertEquals(2000, CheckAliveThread.HEARTBEAT_PACKET_DELAY);
    }

    @Test
    @DisplayName("getInstance 应返回单例实例")
    void testGetInstanceReturnsSingleton() throws Exception {
        Method getInstanceMethod = CheckAliveThread.class.getDeclaredMethod("getInstance");
        getInstanceMethod.setAccessible(true);

        CheckAliveThread instance1 = (CheckAliveThread) getInstanceMethod.invoke(null);
        CheckAliveThread instance2 = (CheckAliveThread) getInstanceMethod.invoke(null);

        assertSame(instance1, instance2);
    }

    @Test
    @DisplayName("startThread 不应抛出异常")
    void testStartThreadDoesNotThrow() {
        assertDoesNotThrow(() -> CheckAliveThread.startThread());
        CheckAliveThread.stopThread();
    }

    @Test
    @DisplayName("stopThread 不应抛出异常")
    void testStopThreadDoesNotThrow() {
        assertDoesNotThrow(() -> CheckAliveThread.stopThread());
    }

    @Test
    @DisplayName("多次调用 startThread 应安全")
    void testMultipleStartThreadCalls() {
        assertDoesNotThrow(() -> {
            CheckAliveThread.startThread();
            CheckAliveThread.startThread();
            CheckAliveThread.startThread();
        });
        CheckAliveThread.stopThread();
    }

    @Test
    @DisplayName("多次调用 stopThread 应安全")
    void testMultipleStopThreadCalls() {
        assertDoesNotThrow(() -> {
            CheckAliveThread.stopThread();
            CheckAliveThread.stopThread();
            CheckAliveThread.stopThread();
        });
    }

    @Test
    @DisplayName("startThread 后 stopThread 应正常工作")
    void testStartThenStop() {
        assertDoesNotThrow(() -> {
            CheckAliveThread.startThread();
            Thread.sleep(100);
            CheckAliveThread.stopThread();
        });
    }
}
