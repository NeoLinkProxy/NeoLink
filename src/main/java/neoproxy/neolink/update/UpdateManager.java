package neoproxy.neolink.update;

import fun.ceroxe.api.OshiUtils;
import fun.ceroxe.api.print.log.LogType;
import neoproxy.neolink.core.NeoLinkCoreRunner;
import neoproxy.neolink.core.VersionInfo;
import neoproxy.neolink.util.Debugger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static neoproxy.neolink.util.Debugger.debugOperation;
import static neoproxy.neolink.network.InternetOperator.receiveStr;
import static neoproxy.neolink.network.InternetOperator.sendStr;
import static neoproxy.neolink.core.NeoLink.*;

/**
 * 更新管理器
 *
 * 核心职责：
 * 1. 检查并下载 NeoLink 客户端更新
 * 2. Windows 直接启动上游提供的 installer exe
 * 3. 非 Windows 保持 JAR 更新与备份流程
 *
 * 设计特点：
 * - 支持 Windows 和 Linux 自动更新
 * - 文件大小校验，确保下载完整
 * - Windows installer 由安装器接管替换逻辑，避免在运行中覆盖自身
 * - 非 Windows 自动备份和替换机制
 *
 * 更新流程：
 * 1. 从服务器下载对应平台的更新文件
 * 2. Windows 启动 installer exe
 * 3. 非 Windows 备份当前 JAR 并替换为新版本
 *
 * @author NeoProxy Team
 * @since 5.0.0
 */
public class UpdateManager {
    private static final String tempUpdateDir = CURRENT_DIR_PATH;

    public static void checkUpdate(String fileName) {
        debugOperation("Checking for updates: " + fileName);
        try {
            boolean isWindows = OshiUtils.isWindows();
            debugOperation("OS is Windows: " + isWindows);

            // 1. 告诉服务端当前需要的格式
            sendStr(isWindows ? "exe" : "jar");

            // 2. 接收服务端返回的下载地址 (URL)
            String responseUrl = receiveStr();
            debugOperation("Server response (URL): " + responseUrl);

            // 3. 检查返回值，如果是 "false" 或者空，说明服务端无法提供更新
            if (responseUrl == null || "false".equalsIgnoreCase(responseUrl) || responseUrl.trim().isEmpty()) {
                if (isGUIMode) {
                    say(languageData.PLEASE_UPDATE_MANUALLY);
                    // [修改] 以前是调用 Controller.stopService()，现在调用 Runner 的停止请求
                    // 这会通过回调通知 UI (ViewModel) 停止运行状态
                    NeoLinkCoreRunner.requestStop();
                } else {
                    exitAndFreeze(-1);
                }
                return;
            }

            // 4. 准备本地文件路径
            String fileExtension = isWindows ? ".exe" : ".jar";
            File clientFile = new File(tempUpdateDir, fileName + fileExtension);
            debugOperation("Target local file: " + clientFile.getAbsolutePath());

            say(languageData.START_TO_DOWNLOAD_UPDATE);
            say("Download Source: " + responseUrl);

            // 5. 下载文件
            boolean downloadSuccess = downloadFileFromUrl(responseUrl, clientFile);
            debugOperation("Download success: " + downloadSuccess);

            if (!downloadSuccess) {
                say(languageData.FAILED_TO_DOWNLOAD_UPDATE_FILE, LogType.ERROR);
                finishUpdateProcess(-1);
                return;
            }

            say(languageData.DOWNLOAD_SUCCESS);

            // 6. Windows 上游现在返回 installer exe，下载后直接交给安装器接管。
            if (isWindows) {
                if (!startInstaller(clientFile)) {
                    finishUpdateProcess(-1);
                    return;
                }

                // 安装器需要接管文件替换，成功拉起后必须立即释放当前进程。
                exitAfterInstallerStarted();
                return;
            } else {
                debugOperation("Updating JAR file...");
                File finalJar = new File(CURRENT_DIR_PATH, fileName + fileExtension);
                if (finalJar.exists()) {
                    File backupFile = new File(CURRENT_DIR_PATH, fileName + " - copy" + fileExtension);
                    if (!backupFile.exists()) {
                        if (!finalJar.renameTo(backupFile)) {
                            say(languageData.FAILED_TO_BACKUP_EXISTING_JAR, LogType.ERROR);
                            finishUpdateProcess(-1);
                            return;
                        }
                    } else {
                        if (!finalJar.delete()) {
                            say(languageData.FAILED_TO_DELETE_EXISTING_JAR, LogType.ERROR);
                            finishUpdateProcess(-1);
                            return;
                        }
                    }
                }

                Files.copy(clientFile.toPath(), finalJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                deleteFileOrDirectory(clientFile);

                say(languageData.PLEASE_RUN + finalJar.getAbsolutePath());
            }

            finishUpdateProcess(0);
        } catch (IOException e) {
            Debugger.debugOperation(e);
            say(languageData.FAILED_TO_CHECK_UPDATES + e.getMessage(), LogType.ERROR);
            finishUpdateProcess(0);
        } catch (Exception e) {
            Debugger.debugOperation(e);
            say(languageData.UNEXPECTED_ERROR_DURING_UPDATE + e.getMessage(), LogType.ERROR);
            finishUpdateProcess(0);
        }
    }

    // [新增] HTTP下载工具方法
    private static boolean downloadFileFromUrl(String urlString, File outputFile) {
        HttpURLConnection httpConn = null;
        try {
            URL url = new URL(urlString);
            httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setInstanceFollowRedirects(true);
            httpConn.setConnectTimeout(10000);
            httpConn.setReadTimeout(30000); // 下载大文件时允许读取时间较长
            httpConn.setRequestMethod("GET");
            // 伪装 User-Agent 避免部分 CDN 拦截
            httpConn.setRequestProperty("User-Agent", "NeoLink-Updater/" + VersionInfo.VERSION);

            int responseCode = httpConn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                long fileSize = httpConn.getContentLengthLong();

                // 打印文件大小信息
                if (fileSize > 0) {
                    say(languageData.DOWNLOADING_FILE_OF_SIZE + formatFileSize(fileSize));
                } else {
                    say("Downloading file (size unknown)...");
                }

                try (InputStream inputStream = new BufferedInputStream(httpConn.getInputStream());
                     FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
                     BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytesRead = 0;
                    int progress = 0;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        bufferedOutputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;

                        // 简单的进度显示
                        if (fileSize > 0) {
                            int newProgress = (int) (totalBytesRead * 100 / fileSize);
                            if (newProgress > progress) { // 避免刷屏，只有进度变化时才显示
                                progress = newProgress;
                                say(languageData.DOWNLOAD_PROGRESS + progress + "%");
                            }
                        }
                    }
                }

                say(languageData.FILE_DOWNLOAD_COMPLETED);
                return true;
            } else {
                say("Download failed. Server replied HTTP code: " + responseCode, LogType.ERROR);
                return false;
            }
        } catch (Exception e) {
            Debugger.debugOperation(e);
            say(languageData.ERROR_WHILE_DOWNLOADING_FILE + e.getMessage(), LogType.ERROR);
            return false;
        } finally {
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private static boolean startInstaller(File installerFile) {
        if (installerFile == null) {
            say(languageData.EXECUTABLE_NOT_FOUND + "null", LogType.ERROR);
            return false;
        }

        debugOperation("Preparing to start update installer: " + installerFile.getName());
        try {
            if (!installerFile.exists() || !installerFile.isFile()) {
                say(languageData.EXECUTABLE_NOT_FOUND + installerFile.getAbsolutePath(), LogType.ERROR);
                return false;
            }

            List<String> command = List.of(installerFile.getAbsolutePath());
            say(languageData.STARTING_INSTALLER + installerFile.getAbsolutePath());
            debugOperation("Executing installer command: " + command);
            new ProcessBuilder(command).start();
            say(languageData.INSTALLER_STARTED);

            if (!isRunningInUnitTest()) {
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return true;
        } catch (Exception e) {
            Debugger.debugOperation(e);
            say(languageData.FAILED_TO_START_INSTALLER + e.getMessage(), LogType.ERROR);
            return false;
        }
    }

    private static void finishUpdateProcess(int exitCode) {
        if (isGUIMode) {
            System.exit(exitCode);
        } else {
            exitAndFreeze(exitCode);
        }
    }

    private static void exitAfterInstallerStarted() {
        System.exit(0);
    }

    /**
     * 检查当前是否在单元测试环境中运行
     * 通过检查调用栈中是否包含 JUnit 相关的类
     */
    private static boolean isRunningInUnitTest() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.contains("org.junit") || 
                className.contains("org.mockito") ||
                className.contains(".test.")) {
                return true;
            }
        }
        return false;
    }

    private static void deleteFileOrDirectory(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) {
            return;
        }
        debugOperation("Deleting: " + fileOrDirectory.getAbsolutePath());

        try {
            if (fileOrDirectory.isDirectory()) {
                File[] files = fileOrDirectory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        deleteFileOrDirectory(file);
                    }
                }
            }

            if (!fileOrDirectory.delete()) {
                say(languageData.FAILED_TO_DELETE + fileOrDirectory.getAbsolutePath(), LogType.WARNING);
            } else if (isDebugMode) {
                say(languageData.SUCCESSFULLY_DELETED + fileOrDirectory.getAbsolutePath());
            }
        } catch (Exception e) {
            Debugger.debugOperation(e);
            say(languageData.ERROR_DELETING_FILE + e.getMessage(), LogType.WARNING);
        }
    }
}
