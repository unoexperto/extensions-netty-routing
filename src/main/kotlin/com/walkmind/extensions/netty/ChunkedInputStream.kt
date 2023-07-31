package com.walkmind.extensions.netty

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.stream.ChunkedInput
import java.io.InputStream

class ChunkedInputStream(private val inputStream: InputStream, private val contentSize: Long) : ChunkedInput<ByteBuf> {

    private val chunkSize = 64 * 1024
    private var totalBytesRead = 0L
    private var isOpen: Boolean = inputStream.available() > 0

    override fun isEndOfInput(): Boolean {
        return !isOpen
    }

    override fun close() {
        inputStream.close()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("readChunk(ctx.alloc())"))
    override fun readChunk(ctx: ChannelHandlerContext): ByteBuf? {
        return readChunk(ctx.alloc())
    }

    override fun readChunk(allocator: ByteBufAllocator): ByteBuf? {

        if (!isOpen)
            return null

        val buffer = allocator.buffer(chunkSize)
        var release = true
        return try {
            var readBytes = 0
            while (true) {
                val localReadBytes = buffer.writeBytes(inputStream, chunkSize - readBytes)
                if (localReadBytes < 0) {
                    isOpen = false
                    break
                }

                readBytes += localReadBytes
                if (readBytes == chunkSize)
                    break
            }
            totalBytesRead += readBytes
            release = false
            buffer
        } finally {
            if (release)
                buffer.release()
        }
    }

    override fun length(): Long {
        return contentSize
    }

    override fun progress(): Long {
        return totalBytesRead
    }
}
