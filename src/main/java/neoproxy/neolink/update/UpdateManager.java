package neoproxy.neolink.update;

import fun.ceroxe.api.OshiUtils;
import fun.ceroxe.api.print.log.LogType;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.core.NeoLinkCoreRunner;
import neoproxy.neolink.core.VersionInfo;
import neoproxy.neolink.util.Debugger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
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
    private static final int DOWNLOAD_BUFFER_SIZE = 8192;
    private static final int MAX_DOWNLOAD_REDIRECTS = 8;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 15000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 300000;

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
                    finishUpdateProcess(-1);
                } else {
                    exitAndFreeze(-1);
                }
                return;
            }

            // 4. 准备本地文件路径
            String fileExtension = isWindows ? ".exe" : ".jar";
            File updateDirectory = resolveUpdateDirectory();
            File fallbackClientFile = new File(updateDirectory, fileName + fileExtension);
            debugOperation("Target update directory: " + updateDirectory.getAbsolutePath());

            say(languageData.START_TO_DOWNLOAD_UPDATE);
            say("Download Source: " + responseUrl);
            say(languageData.UPDATE_DOWNLOAD_TARGET + updateDirectory.getAbsolutePath());

            // 5. 下载文件
            File clientFile = downloadFileFromUrl(responseUrl, fallbackClientFile);
            debugOperation("Downloaded update file: " + (clientFile == null ? "null" : clientFile.getAbsolutePath()));

            if (clientFile == null) {
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
                File finalJar = new File(resolveUpdateDirectory(), fileName + fileExtension);
                if (finalJar.exists()) {
                    File backupFile = new File(resolveUpdateDirectory(), fileName + " - copy" + fileExtension);
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

    private static File downloadFileFromUrl(String urlString, File fallbackOutputFile) {
        try {
            if (urlString == null || urlString.isBlank()) {
                say(languageData.INVALID_DOWNLOAD_URL + urlString, LogType.ERROR);
                return null;
            }
            if (fallbackOutputFile == null) {
                say(languageData.INVALID_DOWNLOAD_TARGET + "null", LogType.ERROR);
                return null;
            }

            URL initialUrl = new URL(urlString.trim());
            if (!isSupportedHttpUrl(initialUrl)) {
                say(languageData.UNSUPPORTED_DOWNLOAD_PROTOCOL + initialUrl.getProtocol(), LogType.ERROR);
                return null;
            }

            File absoluteFallbackOutputFile = fallbackOutputFile.getAbsoluteFile();
            File parent = absoluteFallbackOutputFile.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }

            return downloadFileFollowingRedirects(initialUrl, absoluteFallbackOutputFile);
        } catch (Exception e) {
            Debugger.debugOperation(e);
            say(languageData.ERROR_WHILE_DOWNLOADING_FILE + e.getMessage(), LogType.ERROR);
            return null;
        }
    }

    private static File resolveUpdateDirectory() {
        if (ConfigOperator.BASE_PACKAGE_DIR != null && !ConfigOperator.BASE_PACKAGE_DIR.isBlank()) {
            return new File(ConfigOperator.BASE_PACKAGE_DIR);
        }

        File currentFile = getCurrentFile();
        File currentParent = currentFile != null ? currentFile.getParentFile() : null;
        if (currentParent != null) {
            return currentParent;
        }

        return new File(CURRENT_DIR_PATH);
    }

    private static File createTemporaryDownloadFile(File outputFile) throws IOException {
        File parent = outputFile.getParentFile();
        Path parentPath = parent == null ? Path.of(CURRENT_DIR_PATH) : parent.toPath();
        return Files.createTempFile(parentPath, outputFile.getName() + ".", ".download").toFile();
    }

    private static File downloadFileFollowingRedirects(URL initialUrl, File fallbackOutputFile) throws IOException {
        URL currentUrl = initialUrl;
        int redirects = 0;

        while (true) {
            HttpURLConnection httpConn = openDownloadConnection(currentUrl);
            try {
                int responseCode = httpConn.getResponseCode();

                if (isRedirectResponse(responseCode)) {
                    redirects++;
                    if (redirects > MAX_DOWNLOAD_REDIRECTS) {
                        say(languageData.TOO_MANY_DOWNLOAD_REDIRECTS + MAX_DOWNLOAD_REDIRECTS, LogType.ERROR);
                        return null;
                    }

                    String location = httpConn.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        say(languageData.DOWNLOAD_REDIRECT_WITHOUT_LOCATION + responseCode, LogType.ERROR);
                        return null;
                    }

                    URL redirectedUrl = new URL(currentUrl, location.trim());
                    if (!isSupportedHttpUrl(redirectedUrl)) {
                        say(languageData.UNSUPPORTED_DOWNLOAD_PROTOCOL + redirectedUrl.getProtocol(), LogType.ERROR);
                        return null;
                    }

                    currentUrl = redirectedUrl;
                    say(languageData.DOWNLOAD_REDIRECTED_TO + currentUrl);
                    continue;
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    say(languageData.DOWNLOAD_FAILED_HTTP_CODE + responseCode + " " + safeResponseMessage(httpConn), LogType.ERROR);
                    return null;
                }

                File outputFile = resolveDownloadOutputFile(fallbackOutputFile, currentUrl, httpConn);
                return downloadResponseBodyToFile(httpConn, outputFile);
            } finally {
                httpConn.disconnect();
            }
        }
    }

    private static HttpURLConnection openDownloadConnection(URL url) throws IOException {
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        // 显式处理重定向，才能记录 Location，并覆盖 HTTP->HTTPS 等 JDK 自动跟随不稳定的场景。
        httpConn.setInstanceFollowRedirects(false);
        httpConn.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
        httpConn.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);
        httpConn.setRequestMethod("GET");
        httpConn.setRequestProperty("User-Agent", "NeoLink-Updater/" + VersionInfo.VERSION);
        httpConn.setRequestProperty("Accept", "application/octet-stream,*/*");
        httpConn.setRequestProperty("Accept-Encoding", "identity");
        return httpConn;
    }

    private static File resolveDownloadOutputFile(File fallbackOutputFile, URL finalUrl, HttpURLConnection httpConn) {
        String suggestedFileName = extractFileNameFromContentDisposition(httpConn.getHeaderField("Content-Disposition"));
        if (suggestedFileName == null || suggestedFileName.isBlank()) {
            suggestedFileName = extractFileNameFromUrl(finalUrl);
        }

        String safeFileName = sanitizeDownloadFileName(suggestedFileName, fallbackOutputFile.getName());
        String expectedExtension = extractExtension(fallbackOutputFile.getName());
        if (!expectedExtension.isEmpty()
                && !safeFileName.toLowerCase(Locale.ROOT).endsWith(expectedExtension.toLowerCase(Locale.ROOT))) {
            safeFileName = fallbackOutputFile.getName();
        }

        File parent = fallbackOutputFile.getParentFile();
        return parent == null ? new File(safeFileName).getAbsoluteFile() : new File(parent, safeFileName).getAbsoluteFile();
    }

    private static File downloadResponseBodyToFile(HttpURLConnection httpConn, File outputFile) throws IOException {
        if (outputFile.exists() && outputFile.isDirectory()) {
            say(languageData.INVALID_DOWNLOAD_TARGET + outputFile.getAbsolutePath(), LogType.ERROR);
            return null;
        }

        File parent = outputFile.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }

        File temporaryFile = null;
        boolean movedToDestination = false;
        try {
            // 先下载到同目录临时文件，成功后再替换目标，避免失败时留下半截 installer。
            temporaryFile = createTemporaryDownloadFile(outputFile);
            if (!writeResponseBody(httpConn, temporaryFile)) {
                return null;
            }

            Files.move(temporaryFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            movedToDestination = true;
            say(languageData.UPDATE_SAVED_TO + outputFile.getAbsolutePath());
            return outputFile;
        } finally {
            if (!movedToDestination && temporaryFile != null) {
                deleteFileOrDirectory(temporaryFile);
            }
        }
    }

    private static boolean writeResponseBody(HttpURLConnection httpConn, File outputFile) throws IOException {
        long fileSize = httpConn.getContentLengthLong();

        if (fileSize > 0) {
            say(languageData.DOWNLOADING_FILE_OF_SIZE + formatFileSize(fileSize));
        } else {
            say(languageData.DOWNLOADING_FILE_SIZE_UNKNOWN);
        }

        try (InputStream inputStream = new BufferedInputStream(httpConn.getInputStream());
             OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(outputFile.toPath()))) {

            byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;
            int progress = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                if (fileSize > 0) {
                    int newProgress = (int) (totalBytesRead * 100 / fileSize);
                    if (newProgress > progress) {
                        progress = newProgress;
                        say(languageData.DOWNLOAD_PROGRESS + progress + "%");
                    }
                }
            }
            outputStream.flush();

            if (totalBytesRead <= 0) {
                say(languageData.INVALID_FILE_SIZE_RECEIVED + totalBytesRead, LogType.ERROR);
                return false;
            }
            if (fileSize >= 0 && totalBytesRead != fileSize) {
                say(languageData.FILE_SIZE_MISMATCH + fileSize + ", actual: " + totalBytesRead, LogType.ERROR);
                return false;
            }
        }

        say(languageData.FILE_DOWNLOAD_COMPLETED);
        return true;
    }

    private static String extractFileNameFromContentDisposition(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isBlank()) {
            return null;
        }

        String fallbackFileName = null;
        for (String part : contentDisposition.split(";")) {
            String trimmedPart = part.trim();
            int separatorIndex = trimmedPart.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            String key = trimmedPart.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
            String value = trimmedPart.substring(separatorIndex + 1).trim();
            if ("filename*".equals(key)) {
                String decodedFileName = decodeRfc5987FileName(value);
                if (decodedFileName != null && !decodedFileName.isBlank()) {
                    return decodedFileName;
                }
            } else if ("filename".equals(key)) {
                fallbackFileName = stripQuotes(value);
            }
        }
        return fallbackFileName;
    }

    private static String decodeRfc5987FileName(String value) {
        try {
            String normalizedValue = stripQuotes(value);
            int firstQuote = normalizedValue.indexOf('\'');
            int secondQuote = firstQuote < 0 ? -1 : normalizedValue.indexOf('\'', firstQuote + 1);
            if (firstQuote > 0 && secondQuote > firstQuote) {
                String charsetName = normalizedValue.substring(0, firstQuote);
                String encodedFileName = normalizedValue.substring(secondQuote + 1);
                return URLDecoder.decode(encodedFileName, Charset.forName(charsetName));
            }
            return URLDecoder.decode(normalizedValue, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Debugger.debugOperation(e);
            return null;
        }
    }

    private static String extractFileNameFromUrl(URL url) {
        String path = url.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }

        int lastSlashIndex = path.lastIndexOf('/');
        String rawFileName = lastSlashIndex >= 0 ? path.substring(lastSlashIndex + 1) : path;
        if (rawFileName.isBlank()) {
            return null;
        }

        try {
            return URLDecoder.decode(rawFileName, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            Debugger.debugOperation(e);
            return rawFileName;
        }
    }

    private static String sanitizeDownloadFileName(String fileName, String fallbackFileName) {
        String normalizedFileName = fileName == null || fileName.isBlank() ? fallbackFileName : fileName;
        normalizedFileName = normalizedFileName.replace('\\', '/');
        int lastSlashIndex = normalizedFileName.lastIndexOf('/');
        if (lastSlashIndex >= 0) {
            normalizedFileName = normalizedFileName.substring(lastSlashIndex + 1);
        }

        StringBuilder sanitizedFileName = new StringBuilder(normalizedFileName.length());
        for (int i = 0; i < normalizedFileName.length(); i++) {
            char currentChar = normalizedFileName.charAt(i);
            boolean invalidWindowsFileNameChar = currentChar < 32
                    || currentChar == 127
                    || currentChar == '<'
                    || currentChar == '>'
                    || currentChar == ':'
                    || currentChar == '"'
                    || currentChar == '/'
                    || currentChar == '\\'
                    || currentChar == '|'
                    || currentChar == '?'
                    || currentChar == '*';
            sanitizedFileName.append(invalidWindowsFileNameChar ? '_' : currentChar);
        }

        String result = trimUnsafeTrailingCharacters(sanitizedFileName.toString().trim());
        if (result.isBlank() || ".".equals(result) || "..".equals(result)) {
            return fallbackFileName;
        }
        return isReservedWindowsDeviceName(result) ? "_" + result : result;
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String strippedValue = value.trim();
        if (strippedValue.length() >= 2 && strippedValue.startsWith("\"") && strippedValue.endsWith("\"")) {
            return strippedValue.substring(1, strippedValue.length() - 1);
        }
        return strippedValue;
    }

    private static String trimUnsafeTrailingCharacters(String value) {
        int endExclusive = value.length();
        while (endExclusive > 0) {
            char currentChar = value.charAt(endExclusive - 1);
            if (currentChar != '.' && currentChar != ' ') {
                break;
            }
            endExclusive--;
        }
        return value.substring(0, endExclusive);
    }

    private static boolean isReservedWindowsDeviceName(String fileName) {
        String upperCaseBaseName = fileName;
        int dotIndex = upperCaseBaseName.indexOf('.');
        if (dotIndex >= 0) {
            upperCaseBaseName = upperCaseBaseName.substring(0, dotIndex);
        }
        upperCaseBaseName = upperCaseBaseName.toUpperCase(Locale.ROOT);
        return upperCaseBaseName.equals("CON")
                || upperCaseBaseName.equals("PRN")
                || upperCaseBaseName.equals("AUX")
                || upperCaseBaseName.equals("NUL")
                || upperCaseBaseName.matches("COM[1-9]")
                || upperCaseBaseName.matches("LPT[1-9]");
    }

    private static String extractExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex < 0 ? "" : fileName.substring(lastDotIndex);
    }

    private static boolean isRedirectResponse(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_MOVED_PERM
                || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                || responseCode == 307
                || responseCode == 308;
    }

    private static boolean isSupportedHttpUrl(URL url) {
        String protocol = url.getProtocol();
        return "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
    }

    private static String safeResponseMessage(HttpURLConnection httpConn) {
        try {
            String message = httpConn.getResponseMessage();
            return message == null ? "" : message;
        } catch (IOException e) {
            return "";
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
            NeoLinkCoreRunner.requestStop();
            closeActiveConnectionForGuiUpdateExit();
        } else {
            exitAndFreeze(exitCode);
        }
    }

    private static void exitAfterInstallerStarted() {
        System.exit(0);
    }

    private static void closeActiveConnectionForGuiUpdateExit() {
        try {
            if (connectingSocket != null) {
                connectingSocket.close();
                connectingSocket = null;
            }
            if (hookSocket != null) {
                hookSocket.close();
                hookSocket = null;
            }
        } catch (Exception e) {
            Debugger.debugOperation(e);
        }
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
