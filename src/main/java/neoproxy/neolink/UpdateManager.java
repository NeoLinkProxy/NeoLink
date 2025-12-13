package neoproxy.neolink;

import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import plethora.os.detect.OSDetector;
import plethora.os.windowsSystem.WindowsOperation;
import plethora.print.log.LogType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

import static neoproxy.neolink.Debugger.debugOperation;
import static neoproxy.neolink.InternetOperator.*;
import static neoproxy.neolink.NeoLink.*;

public class UpdateManager {
    private static final String tempUpdateDir = CURRENT_DIR_PATH;

    public static void checkUpdate(String fileName) {
        debugOperation("Checking for updates: " + fileName);
        try {
            boolean isWindows = OSDetector.isWindows();
            debugOperation("OS is Windows: " + isWindows);
            sendStr(isWindows ? "7z" : "jar");
            boolean canDownload = Boolean.parseBoolean(receiveStr());
            debugOperation("Server response for download availability: " + canDownload);

            if (!canDownload) {
                if (isGUIMode) {
                    say(languageData.PLEASE_UPDATE_MANUALLY);
                    mainWindowController.stopService();
                } else {
                    exitAndFreeze(-1);
                }
                return;
            }

            String fileExtension = isWindows ? ".7z" : ".jar";
            File clientFile = new File(tempUpdateDir, fileName + fileExtension);
            debugOperation("Target update file: " + clientFile.getAbsolutePath());

            say(languageData.START_TO_DOWNLOAD_UPDATE);

            boolean downloadSuccess = receiveFileInChunks(clientFile);
            debugOperation("Download success: " + downloadSuccess);

            if (!downloadSuccess) {
                say(languageData.FAILED_TO_DOWNLOAD_UPDATE_FILE, LogType.ERROR);
                exitAndFreeze(-1);
                return;
            }

            say(languageData.DOWNLOAD_SUCCESS);

            if (isWindows) {
                debugOperation("Extracting 7z file...");
                boolean extractionSuccess = extractSevenZFile(clientFile, new File(CURRENT_DIR_PATH));
                if (!extractionSuccess) {
                    say(languageData.FAILED_TO_EXTRACT_7Z_FILE, LogType.ERROR);
                    exitAndFreeze(-1);
                    return;
                }

                File extractedExe = findExtractedExe(new File(CURRENT_DIR_PATH));
                if (extractedExe == null) {
                    debugOperation("Extracted EXE not found.");
                    say(languageData.NEOLINK_EXE_NOT_FOUND, LogType.ERROR);
                    exitAndFreeze(-1);
                    return;
                }

                deleteFileOrDirectory(clientFile);
                debugOperation("Deleted temporary archive.");

                startNewVersion(extractedExe);
            } else {
                debugOperation("Updating JAR file...");
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
                deleteFileOrDirectory(clientFile);

                say(languageData.PLEASE_RUN + finalJar.getAbsolutePath());
            }

            exitAndFreeze(0);
        } catch (IOException e) {
            Debugger.debugOperation(e);
            say(languageData.FAILED_TO_CHECK_UPDATES + e.getMessage(), LogType.ERROR);
            exitAndFreeze(0);
        } catch (Exception e) {
            Debugger.debugOperation(e);
            say(languageData.UNEXPECTED_ERROR_DURING_UPDATE + e.getMessage(), LogType.ERROR);
            exitAndFreeze(0);
        }
    }

    private static boolean receiveFileInChunks(File outputFile) {
        debugOperation("Starting file reception in chunks.");
        try {
            String fileSizeStr = receiveStr();
            debugOperation("Received file size string: " + fileSizeStr);
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

            try (BufferedOutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                long totalBytesRead = 0;
                int progress = 0;

                while (totalBytesRead < fileSize) {
                    byte[] chunk = receiveBytes();
                    if (chunk == null) {
                        say(languageData.CONNECTION_CLOSED_PREMATURELY, LogType.ERROR);
                        return false;
                    }

                    fileOutputStream.write(chunk);
                    totalBytesRead += chunk.length;

                    int newProgress = (int) (totalBytesRead * 100 / fileSize);
                    if (newProgress > progress) {
                        progress = newProgress;
                        say(languageData.DOWNLOAD_PROGRESS + progress + "%");
                        // debugOperation("Download progress: " + progress + "%"); // Too chatty
                    }
                }

                if (totalBytesRead != fileSize) {
                    say(languageData.FILE_SIZE_MISMATCH + fileSize + ", Received: " + totalBytesRead, LogType.ERROR);
                    return false;
                }

                say(languageData.FILE_DOWNLOAD_COMPLETED);
            } catch (Exception e) {
                Debugger.debugOperation(e);
                say(languageData.ERROR_WHILE_DOWNLOADING_FILE + e.getMessage(), LogType.ERROR);
                return false;
            }

            return true;
        } catch (Exception e) {
            Debugger.debugOperation(e);
            say(languageData.ERROR_RECEIVING_FILE + e.getMessage(), LogType.ERROR);
            return false;
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

    private static boolean extractSevenZFile(File sevenZFile, File destination) {
        debugOperation("Starting 7z extraction.");
        RandomAccessFile randomAccessFile = null;
        IInArchive inArchive = null;

        try {
            SevenZip.initSevenZipFromPlatformJAR();
            randomAccessFile = new RandomAccessFile(sevenZFile, "r");
            inArchive = SevenZip.openInArchive(null, new RandomAccessFileInStream(randomAccessFile));

            int[] in = new int[inArchive.getNumberOfItems()];
            for (int i = 0; i < in.length; i++) {
                in[i] = i;
            }

            IInArchive finalInArchive = inArchive;
            inArchive.extract(in, false, new IArchiveExtractCallback() {
                private FileOutputStream outputStream;
                private File currentFile;

                @Override
                public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
                    if (extractAskMode != ExtractAskMode.EXTRACT) {
                        return null;
                    }
                    String path = finalInArchive.getStringProperty(index, PropID.PATH);
                    boolean isFolder = (Boolean) finalInArchive.getProperty(index, PropID.IS_FOLDER);

                    if (isFolder) {
                        return null;
                    }

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
                }

                @Override
                public void setTotal(long total) throws SevenZipException {
                }
            });

            say(languageData.SEVENZ_FILE_EXTRACTED_SUCCESSFULLY + destination.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Debugger.debugOperation(e);
            say(languageData.FAILED_TO_EXTRACT_7Z_FILE + e.getMessage(), LogType.ERROR);
            return false;
        } finally {
            try {
                if (inArchive != null) inArchive.close();
                if (randomAccessFile != null) randomAccessFile.close();
            } catch (IOException e) {
                Debugger.debugOperation(e);
                say(languageData.ERROR_CLOSING_7Z_FILE + e.getMessage(), LogType.WARNING);
            }
        }
    }

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

    private static void startNewVersion(File exeFile) {
        debugOperation("Preparing to start new version: " + exeFile.getName());
        try {
            if (!exeFile.exists() || !exeFile.isFile()) {
                say(languageData.EXECUTABLE_NOT_FOUND + exeFile.getAbsolutePath(), LogType.ERROR);
                return;
            }

            StringBuilder command = new StringBuilder("cmd.exe /c start \"\" \"");
            command.append(exeFile.getAbsolutePath()).append("\"");

            if (key != null) {
                command.append(" --key=").append(key);
            }
            if (localPort != INVALID_LOCAL_PORT) {
                command.append(" --local-port=").append(localPort);
            }
            if (!isGUIMode) {
                command.append(" --nogui");
            }
            if (isDebugMode) {
                command.append(" --debug");
            }
            if (outputFilePath != null) {
                command.append(" --output-file=").append(outputFilePath);
            }

            say(languageData.STARTING_NEW_VERSION + command);
            debugOperation("Executing command: " + command);
            WindowsOperation.run(command.toString());
            say(languageData.NEW_VERSION_STARTED);

            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.exit(0);
        } catch (Exception e) {
            Debugger.debugOperation(e);
            say(languageData.FAILED_TO_START_NEW_VERSION + e.getMessage(), LogType.ERROR);
        }
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