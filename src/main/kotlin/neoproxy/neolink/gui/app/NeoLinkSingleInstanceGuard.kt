package neoproxy.neolink.gui.app
import neoproxy.neolink.app.ApplicationFiles
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

class NeoLinkSingleInstanceGuard private constructor(
    private val channel: FileChannel,
    private val lock: FileLock
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        fun acquire(): NeoLinkSingleInstanceGuard? {
            return acquire(ApplicationFiles.lockFile())
        }

        internal fun acquire(lockFile: File): NeoLinkSingleInstanceGuard? {
            lockFile.parentFile?.toPath()?.let { parent ->
                Files.createDirectories(parent)
            }
            val channel = FileChannel.open(
                lockFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            )
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return NeoLinkSingleInstanceGuard(channel, lock)
        }
    }
}
