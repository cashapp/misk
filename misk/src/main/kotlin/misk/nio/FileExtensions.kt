package misk.nio

import java.nio.channels.FileChannel

fun <T> FileChannel.withLock(
    shared: Boolean,
    action: () -> T
) =
    lock(0, Long.MAX_VALUE, shared).use { action() }


