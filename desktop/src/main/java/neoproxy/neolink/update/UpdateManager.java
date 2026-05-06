package neoproxy.neolink.update;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.app.LanguageManager;
import neoproxy.neolink.cli.ClientConsole;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.core.NeoLinkCoreRunner;
import neoproxy.neolink.core.VersionInfo;
import neoproxy.neolink.util.Debugger;
import top.ceroxe.api.print.log.LogType;

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
import neoproxy.neolink.state.FeatureState;
import neoproxy.neolink.state.RuntimeState;

/**
 * 更新下载与交接流程（update download & handoff workflow）。
 *
 * <p>本类保留更新链路的全部 I/O 细节：下载、重定向、安装器拉起、JAR 自替换。按用户要求，
 * 这里继续信任上游返回的任意 HTTP / HTTPS 源，不回退这部分行为。</p>
 */
public class UpdateManager {
    private static final String EXECUTABLE_EXTENSION = ".exe";
    private static final String JAR_EXTENSION = ".jar";
    private static final int DOWNLOAD_BUFFER_SIZE = 8192;
    private static final int MAX_DOWNLOAD_REDIRECTS = 8;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 15000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 300000;

    private static LanguageData messages() {
        if (RuntimeState.languageData() == null) {
            LanguageManager.detectLanguage();
        }
        return RuntimeState.languageData();
    }

    private static boolean guiMode() {
        return FeatureState.snapshot().guiMode();
    }

    private static boolean debugMode() {
        return FeatureState.snapshot().debugMode();
    }

    public static void checkUpdate(String fileName, String responseUrl) {
        debugOperation("Checking for updates: " + fileName);
        try {                // 安装器在启动后接管替换流程，因此这个 JVM 会立即退出。
            debugOperation("Server response (URL): " + responseUrl);

            if (responseUrl == null || "false".equalsIgnoreCase(responseUrl) || responseUrl.trim().isEmpty()) {
                if (guiMode()) {
                    ClientConsole.say(messages().PLEASE_UPDATE_MANUALLY);
                    finishUpdateProcess(-1);
                } else {
                    ClientConsole.exitAndFreeze(-1);
                }
                return;
            }

            File updateDirectory = resolveUpdateDirectory();
            File fallbackClientFile = new File(updateDirectory, fileName + inferUpdateExtension(responseUrl));
            debugOperation("Target update directory: " + updateDirectory.getAbsolutePath());

            ClientConsole.say(messages().START_TO_DOWNLOAD_UPDATE);
            ClientConsole.say("Download Source: " + responseUrl);
            ClientConsole.say(messages().UPDATE_DOWNLOAD_TARGET + updateDirectory.getAbsolutePath());

            DownloadedArtifact artifact = downloadUpdateArtifact(responseUrl, fallbackClientFile);
            File clientFile = artifact == null ? null : artifact.file();
            debugOperation("Downloaded update file: " + (clientFile == null ? "null" : clientFile.getAbsolutePath()));

            if (clientFile == null) {
                ClientConsole.say(messages().FAILED_TO_DOWNLOAD_UPDATE_FILE, LogType.ERROR);
                finishUpdateProcess(-1);
                return;
            }

            ClientConsole.say(messages().DOWNLOAD_SUCCESS);

            if (isExecutableUpdate(clientFile)) {
                if (!startInstaller(clientFile)) {
                    finishUpdateProcess(-1);
                    return;
                }

                exitAfterInstallerStarted();
                return;
            }

            if (!isJarUpdate(clientFile)) {
                ClientConsole.say(messages().UNSUPPORTED_UPDATE_FILE_TYPE + extractExtension(clientFile.getName()), LogType.ERROR);
                finishUpdateProcess(-1);
                return;
            }

            debugOperation("Updating JAR file...");
            File finalJar = new File(resolveUpdateDirectory(), fileName + JAR_EXTENSION);
            if (finalJar.exists()) {
                File backupFile = new File(resolveUpdateDirectory(), fileName + " - copy" + JAR_EXTENSION);
                if (!backupFile.exists()) {
                    if (!finalJar.renameTo(backupFile)) {
                        ClientConsole.say(messages().FAILED_TO_BACKUP_EXISTING_JAR, LogType.ERROR);
                        finishUpdateProcess(-1);
                        return;
                    }
                } else {
                    if (!finalJar.delete()) {
                        ClientConsole.say(messages().FAILED_TO_DELETE_EXISTING_JAR, LogType.ERROR);
                        finishUpdateProcess(-1);
                        return;
                    }
                }
            }

            Files.copy(clientFile.toPath(), finalJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            deleteFileOrDirectory(clientFile);

            ClientConsole.say(messages().PLEASE_RUN + finalJar.getAbsolutePath());
            finishUpdateProcess(0);
        } catch (IOException e) {
            Debugger.debugOperation(e);
            ClientConsole.say(userFacingFailure(messages().FAILED_TO_CHECK_UPDATES), LogType.ERROR);
            finishUpdateProcess(-1);
        } catch (Exception e) {
            Debugger.debugOperation(e);
            ClientConsole.say(userFacingFailure(messages().UNEXPECTED_ERROR_DURING_UPDATE), LogType.ERROR);
            finishUpdateProcess(-1);
        }
    }

    private static File downloadFileFromUrl(String urlString, File fallbackOutputFile) {
        DownloadedArtifact artifact = downloadUpdateArtifact(urlString, fallbackOutputFile);
        return artifact == null ? null : artifact.file();
    }

    private static DownloadedArtifact downloadUpdateArtifact(String urlString, File fallbackOutputFile) {
        try {
            if (urlString == null || urlString.isBlank()) {
                ClientConsole.say(messages().INVALID_DOWNLOAD_URL + urlString, LogType.ERROR);
                return null;
            }
            if (fallbackOutputFile == null) {
                ClientConsole.say(messages().INVALID_DOWNLOAD_TARGET + "null", LogType.ERROR);
                return null;
            }

            URL initialUrl = new URL(urlString.trim());
            if (!isSupportedHttpUrl(initialUrl)) {
                ClientConsole.say(messages().UNSUPPORTED_DOWNLOAD_PROTOCOL + initialUrl.getProtocol(), LogType.ERROR);
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
            ClientConsole.say(userFacingFailure(messages().ERROR_WHILE_DOWNLOADING_FILE), LogType.ERROR);
            return null;
        }
    }

    private static File resolveUpdateDirectory() {
        return ConfigOperator.resolveWritableRuntimeDirectory();
    }

    private static File createTemporaryDownloadFile(File outputFile) throws IOException {
        File parent = outputFile.getParentFile();
        Path parentPath = parent == null ? Path.of(NeoLink.CURRENT_DIR_PATH) : parent.toPath();
        return Files.createTempFile(parentPath, outputFile.getName() + ".", ".download").toFile();
    }

    private static DownloadedArtifact downloadFileFollowingRedirects(URL initialUrl, File fallbackOutputFile) throws IOException {
        URL currentUrl = initialUrl;
        int redirects = 0;

        while (true) {
            HttpURLConnection httpConn = openDownloadConnection(currentUrl);
            try {
                int responseCode = httpConn.getResponseCode();

                if (isRedirectResponse(responseCode)) {
                    redirects++;
                    if (redirects > MAX_DOWNLOAD_REDIRECTS) {
                        ClientConsole.say(messages().TOO_MANY_DOWNLOAD_REDIRECTS + MAX_DOWNLOAD_REDIRECTS, LogType.ERROR);
                        return null;
                    }

                    String location = httpConn.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        ClientConsole.say(messages().DOWNLOAD_REDIRECT_WITHOUT_LOCATION + responseCode, LogType.ERROR);
                        return null;
                    }

                    URL redirectedUrl = new URL(currentUrl, location.trim());
                    if (!isSupportedHttpUrl(redirectedUrl)) {
                        ClientConsole.say(messages().UNSUPPORTED_DOWNLOAD_PROTOCOL + redirectedUrl.getProtocol(), LogType.ERROR);
                        return null;
                    }

                    currentUrl = redirectedUrl;
                    ClientConsole.say(messages().DOWNLOAD_REDIRECTED_TO + currentUrl);
                    continue;
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    ClientConsole.say(messages().DOWNLOAD_FAILED_HTTP_CODE + responseCode + " " + safeResponseMessage(httpConn), LogType.ERROR);
                    return null;
                }

                File outputFile = resolveDownloadOutputFile(fallbackOutputFile, currentUrl, httpConn);
                File downloadedFile = downloadResponseBodyToFile(httpConn, outputFile);
                if (downloadedFile == null) {
                    return null;
                }
                return new DownloadedArtifact(
                        downloadedFile
                );
            } finally {
                httpConn.disconnect();
            }
        }
    }

    private static HttpURLConnection openDownloadConnection(URL url) throws IOException {
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();        // 手动处理重定向，确保任意 HTTP 和 HTTPS 源都能继续被接受。
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
        File parent = fallbackOutputFile.getParentFile();
        return parent == null ? new File(safeFileName).getAbsoluteFile() : new File(parent, safeFileName).getAbsoluteFile();
    }

    private static File downloadResponseBodyToFile(HttpURLConnection httpConn, File outputFile) throws IOException {
        if (outputFile.exists() && outputFile.isDirectory()) {
            ClientConsole.say(messages().INVALID_DOWNLOAD_TARGET + outputFile.getAbsolutePath(), LogType.ERROR);
            return null;
        }

        File parent = outputFile.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }

        File temporaryFile = null;
        boolean movedToDestination = false;
        try {
            temporaryFile = createTemporaryDownloadFile(outputFile);
            if (!writeResponseBody(httpConn, temporaryFile)) {
                return null;
            }

            Files.move(temporaryFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            movedToDestination = true;
            ClientConsole.say(messages().UPDATE_SAVED_TO + outputFile.getAbsolutePath());
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
            ClientConsole.say(messages().DOWNLOADING_FILE_OF_SIZE + formatFileSize(fileSize));
        } else {
            ClientConsole.say(messages().DOWNLOADING_FILE_SIZE_UNKNOWN);
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
                        ClientConsole.say(messages().DOWNLOAD_PROGRESS + progress + "%");
                    }
                }
            }
            outputStream.flush();

            if (totalBytesRead <= 0) {
                ClientConsole.say(messages().INVALID_FILE_SIZE_RECEIVED + totalBytesRead, LogType.ERROR);
                return false;
            }
            if (fileSize >= 0 && totalBytesRead != fileSize) {
                ClientConsole.say(messages().FILE_SIZE_MISMATCH + fileSize + ", actual: " + totalBytesRead, LogType.ERROR);
                return false;
            }
        }

        ClientConsole.say(messages().FILE_DOWNLOAD_COMPLETED);
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

    private static String inferUpdateExtension(String responseUrl) {
        try {
            String extension = extractExtension(extractFileNameFromUrl(new URL(responseUrl)));
            if (EXECUTABLE_EXTENSION.equalsIgnoreCase(extension) || JAR_EXTENSION.equalsIgnoreCase(extension)) {
                return extension.toLowerCase(Locale.ROOT);
            }
        } catch (Exception e) {
            Debugger.debugOperation(e);
        }
        return JAR_EXTENSION;
    }

    private static boolean isExecutableUpdate(File file) {
        return hasExtension(file, EXECUTABLE_EXTENSION);
    }

    private static boolean isJarUpdate(File file) {
        return hasExtension(file, JAR_EXTENSION);
    }

    private static boolean hasExtension(File file, String expectedExtension) {
        return file != null
                && file.getName().toLowerCase(Locale.ROOT).endsWith(expectedExtension.toLowerCase(Locale.ROOT));
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
            ClientConsole.say(messages().EXECUTABLE_NOT_FOUND + "null", LogType.ERROR);
            return false;
        }

        debugOperation("Preparing to start update installer: " + installerFile.getName());
        try {
            if (!installerFile.exists() || !installerFile.isFile()) {
                ClientConsole.say(messages().EXECUTABLE_NOT_FOUND + installerFile.getAbsolutePath(), LogType.ERROR);
                return false;
            }

            List<String> command = List.of(installerFile.getAbsolutePath());
            ClientConsole.say(messages().STARTING_INSTALLER + installerFile.getAbsolutePath());
            debugOperation("Executing installer command: " + command);
            new ProcessBuilder(command).start();
            ClientConsole.say(messages().INSTALLER_STARTED);

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
            ClientConsole.say(userFacingFailure(messages().FAILED_TO_START_INSTALLER), LogType.ERROR);
            return false;
        }
    }

    private static void finishUpdateProcess(int exitCode) {
        if (guiMode()) {
            NeoLinkCoreRunner.requestStop();
        } else {
            ClientConsole.exitAndFreeze(exitCode);
        }
    }

    private static void exitAfterInstallerStarted() {
        NeoLink.requestExit(0);
    }

    /**
     * 检测单元测试执行环境，避免更新交接流程意外终止测试 JVM。
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
                ClientConsole.say(messages().FAILED_TO_DELETE + fileOrDirectory.getAbsolutePath(), LogType.WARNING);
            } else if (debugMode()) {
                ClientConsole.say(messages().SUCCESSFULLY_DELETED + fileOrDirectory.getAbsolutePath());
            }
        } catch (Exception e) {
            Debugger.debugOperation(e);
            ClientConsole.say(userFacingFailure(messages().ERROR_DELETING_FILE), LogType.WARNING);
        }
    }

    private static String userFacingFailure(String messagePrefix) {
        if (messagePrefix == null) {
            return "";
        }
        return messagePrefix.stripTrailing();
    }

    private record DownloadedArtifact(File file) {
    }
}
