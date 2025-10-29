package neoproject.neolink;

import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import plethora.os.detect.OSDetector;
import plethora.os.windowsSystem.WindowsOperation;
import plethora.print.log.LogType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

import static neoproject.neolink.InternetOperator.receiveBytes;
import static neoproject.neolink.InternetOperator.receiveStr;
import static neoproject.neolink.InternetOperator.sendStr;
import static neoproject.neolink.NeoLink.*;

public class UpdateManager {
    private static final String tempUpdateDir = CURRENT_DIR_PATH; // 临时更新目录，默认为程序自身目录
    private static final int CHUNK_SIZE = 8192; // 8KB块大小，与服务端一致

    public static void checkUpdate(String fileName) {
        if (!enableAutoUpdate) {
            say(languageData.PLEASE_UPDATE_MANUALLY, LogType.ERROR);
            if (isGUIMode) {
                mainWindowController.stopService();
                return;
            } else {
                exitAndFreeze(2);
            }
        }

        try {
            boolean isWindows = OSDetector.isWindows();
            // 告知服务端客户端类型 (exe 或 jar)
            sendStr(isWindows ? "exe" : "jar");
            boolean canDownload = Boolean.parseBoolean(receiveStr());
            if (!canDownload) {
                exitAndFreeze(-1);
                return;
            }

            // 确定新客户端文件的完整路径
            String fileExtension = isWindows ? ".7z" : ".jar";
            File clientFile = new File(tempUpdateDir, fileName + fileExtension);

            say(languageData.START_TO_DOWNLOAD_UPDATE);

            // 分块接收文件
            boolean downloadSuccess = receiveFileInChunks(clientFile);
            if (!downloadSuccess) {
                say(languageData.FAILED_TO_DOWNLOAD_UPDATE_FILE, LogType.ERROR);
                exitAndFreeze(-1);
                return;
            }

            say(languageData.DOWNLOAD_SUCCESS);

            if (isWindows) {
                // 处理7z文件 - 直接解压到当前目录
                boolean extractionSuccess = extractSevenZFile(clientFile, new File(CURRENT_DIR_PATH));
                if (!extractionSuccess) {
                    say(languageData.FAILED_TO_EXTRACT_7Z_FILE, LogType.ERROR);
                    exitAndFreeze(-1);
                    return;
                }

                // 查找解压后的NeoLink.exe
                File extractedExe = findExtractedExe(new File(CURRENT_DIR_PATH));
                if (extractedExe == null) {
                    say(languageData.NEOLINK_EXE_NOT_FOUND, LogType.ERROR);
                    exitAndFreeze(-1);
                    return;
                }

                // 删除7z文件
                deleteFileOrDirectory(clientFile);

                // 启动新版本
                startNewVersion(extractedExe);
            } else {
                // 非Windows系统处理jar文件
                File finalJar = new File(CURRENT_DIR_PATH, fileName + fileExtension);
                if (finalJar.exists()) {
                    File backupFile = new File(CURRENT_DIR_PATH, fileName + " - copy" + fileExtension);
                    if (!backupFile.exists()) {
                        if (!finalJar.renameTo(backupFile)) {
                            say(languageData.FAILED_TO_BACKUP_EXISTING_JAR, LogType.ERROR);
                            exitAndFreeze(-1);
                            return;
                        }
                    } else {
                        if (!finalJar.delete()) {
                            say(languageData.FAILED_TO_DELETE_EXISTING_JAR, LogType.ERROR);
                            exitAndFreeze(-1);
                            return;
                        }
                    }
                }

                Files.copy(clientFile.toPath(), finalJar.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // 只删除临时jar文件
                deleteFileOrDirectory(clientFile);

                say(languageData.PLEASE_RUN + finalJar.getAbsolutePath());
            }

            exitAndFreeze(0);
        } catch (IOException e) {
            debugOperation(e);
            say(languageData.FAILED_TO_CHECK_UPDATES + e.getMessage(), LogType.ERROR);
            exitAndFreeze(0);
        } catch (Exception e) {
            debugOperation(e);
            say(languageData.UNEXPECTED_ERROR_DURING_UPDATE + e.getMessage(), LogType.ERROR);
            exitAndFreeze(0);
        }
    }

    /**
     * 分块接收文件
     *
     * @param outputFile 输出文件
     * @return 是否接收成功
     */
    private static boolean receiveFileInChunks(File outputFile) {
        try {
            // 接收文件大小（使用字符串形式）
            String fileSizeStr = receiveStr();
            long fileSize;
            try {
                fileSize = Long.parseLong(fileSizeStr);
            } catch (NumberFormatException e) {
                say(languageData.INVALID_FILE_SIZE_RECEIVED + fileSizeStr, LogType.ERROR);
                return false;
            }

            if (fileSize < 0) {
                say(languageData.INVALID_FILE_SIZE_RECEIVED + fileSize, LogType.ERROR);
                return false;
            }

            say(languageData.DOWNLOADING_FILE_OF_SIZE + formatFileSize(fileSize));

            // 创建输出文件
            try (BufferedOutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                long totalBytesRead = 0;
                int progress = 0;

                // 循环接收数据块
                while (totalBytesRead < fileSize) {
                    byte[] chunk = receiveBytes();
                    if (chunk == null) {
                        say(languageData.CONNECTION_CLOSED_PREMATURELY, LogType.ERROR);
                        return false;
                    }

                    fileOutputStream.write(chunk);
                    totalBytesRead += chunk.length;

                    // 显示进度（每10%更新一次）
                    int newProgress = (int) (totalBytesRead * 100 / fileSize);
                    if (newProgress > progress) {
                        progress = newProgress;
                        say(languageData.DOWNLOAD_PROGRESS + progress + "%");
                    }
                }

                // 确保接收了所有数据
                if (totalBytesRead != fileSize) {
                    say(languageData.FILE_SIZE_MISMATCH + fileSize + ", Received: " + totalBytesRead, LogType.ERROR);
                    return false;
                }

                // 文件接收完成，不再尝试接收MD5校验和
                say(languageData.FILE_DOWNLOAD_COMPLETED);
            } catch (Exception e) {
                debugOperation(e);
                say(languageData.ERROR_WHILE_DOWNLOADING_FILE + e.getMessage(), LogType.ERROR);
                return false;
            }

            return true;
        } catch (Exception e) {
            debugOperation(e);
            say(languageData.ERROR_RECEIVING_FILE + e.getMessage(), LogType.ERROR);
            return false;
        }
    }

    /**
     * 格式化文件大小为可读字符串
     *
     * @param bytes 字节数
     * @return 格式化后的字符串
     */
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

    /**
     * 使用7-Zip-JBinding解压7z文件
     *
     * @param sevenZFile  7z文件
     * @param destination 目标目录
     * @return 解压是否成功
     */
    private static boolean extractSevenZFile(File sevenZFile, File destination) {
        RandomAccessFile randomAccessFile = null;
        IInArchive inArchive = null;

        try {
            // 初始化7-Zip-JBinding库
            SevenZip.initSevenZipFromPlatformJAR();

            // 打开7z文件
            randomAccessFile = new RandomAccessFile(sevenZFile, "r");
            inArchive = SevenZip.openInArchive(null, new RandomAccessFileInStream(randomAccessFile));

            // 获取归档中的文件数量
            int[] in = new int[inArchive.getNumberOfItems()];
            for (int i = 0; i < in.length; i++) {
                in[i] = i;
            }

            // 提取所有文件
            IInArchive finalInArchive = inArchive;
            inArchive.extract(in, false, new IArchiveExtractCallback() {
                private FileOutputStream outputStream;
                private File currentFile;

                @Override
                public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
                    if (extractAskMode != ExtractAskMode.EXTRACT) {
                        return null;
                    }

                    // 获取文件路径
                    String path = finalInArchive.getStringProperty(index, PropID.PATH);
                    boolean isFolder = (Boolean) finalInArchive.getProperty(index, PropID.IS_FOLDER);

                    if (isFolder) {
                        return null;
                    }

                    // 创建目标文件
                    currentFile = new File(destination, path);
                    File parent = currentFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        if (!parent.mkdirs()) {
                            say(languageData.FAILED_TO_CREATE_DIRECTORY + parent.getAbsolutePath(), LogType.ERROR);
                            throw new SevenZipException("Failed to create directory: " + parent.getAbsolutePath());
                        }
                    }

                    try {
                        outputStream = new FileOutputStream(currentFile);
                        return data -> {
                            try {
                                outputStream.write(data);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return data.length;
                        };
                    } catch (IOException e) {
                        throw new SevenZipException(e);
                    }
                }

                @Override
                public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException {
                    // 准备操作
                }

                @Override
                public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                            outputStream = null;
                        } catch (IOException e) {
                            throw new SevenZipException(e);
                        }
                    }

                    if (extractOperationResult != ExtractOperationResult.OK) {
                        throw new SevenZipException("Extraction failed for file: " + currentFile.getAbsolutePath());
                    }
                }

                @Override
                public void setCompleted(long completeValue) throws SevenZipException {
                    // 完成进度
                }

                @Override
                public void setTotal(long total) throws SevenZipException {
                    // 总进度
                }
            });

            say(languageData.SEVENZ_FILE_EXTRACTED_SUCCESSFULLY + destination.getAbsolutePath());
            return true;
        } catch (Exception e) {
            debugOperation(e);
            say(languageData.FAILED_TO_EXTRACT_7Z_FILE + e.getMessage(), LogType.ERROR);
            return false;
        } finally {
            // 确保资源被正确关闭
            try {
                if (inArchive != null) {
                    inArchive.close();
                }
                if (randomAccessFile != null) {
                    randomAccessFile.close();
                }
            } catch (IOException e) {
                debugOperation(e);
                say(languageData.ERROR_CLOSING_7Z_FILE + e.getMessage(), LogType.WARNING);
            }
        }
    }

    /**
     * 查找解压后的NeoLink.exe文件
     *
     * @param directory 搜索目录
     * @return 找到的exe文件，如果未找到则返回null
     */
    private static File findExtractedExe(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return null;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归搜索子目录
                File result = findExtractedExe(file);
                if (result != null) {
                    return result;
                }
            } else if (file.getName().equalsIgnoreCase("NeoLink.exe") && file.isFile()) {
                return file;
            }
        }

        return null;
    }

    /**
     * 启动新版本
     *
     * @param exeFile 新版本exe文件
     */
    private static void startNewVersion(File exeFile) {
        try {
            // 确保文件存在且可执行
            if (!exeFile.exists() || !exeFile.isFile()) {
                say(languageData.EXECUTABLE_NOT_FOUND + exeFile.getAbsolutePath(), LogType.ERROR);
                return;
            }

            // 构建命令，确保路径被正确引用
            StringBuilder command = new StringBuilder("cmd.exe /c start \"\" \"");
            command.append(exeFile.getAbsolutePath()).append("\"");

            // 传递原有参数
            if (key != null) {
                command.append(" --key=").append(key);
            }
            if (localPort != INVALID_LOCAL_PORT) {
                command.append(" --local-port=").append(localPort);
            }
            if (!isGUIMode) {
                command.append(" --nogui");
            }
            if (isDebugMode){
                command.append(" --debug");
            }
            if (outputFilePath!=null){
                command.append(" --output-file=").append(outputFilePath);
            }

            say(languageData.STARTING_NEW_VERSION + command);
            WindowsOperation.run(command.toString());
            say(languageData.NEW_VERSION_STARTED);

            // 给新版本一些时间启动
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.exit(0);
        } catch (Exception e) {
            debugOperation(e);
            say(languageData.FAILED_TO_START_NEW_VERSION + e.getMessage(), LogType.ERROR);
        }
    }

    /**
     * 删除文件或目录（仅删除指定的文件，不删除其他任何东西）
     *
     * @param fileOrDirectory 要删除的文件或目录
     */
    private static void deleteFileOrDirectory(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) {
            return;
        }

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
            debugOperation(e);
            say(languageData.ERROR_DELETING_FILE + e.getMessage(), LogType.WARNING);
        }
    }
}