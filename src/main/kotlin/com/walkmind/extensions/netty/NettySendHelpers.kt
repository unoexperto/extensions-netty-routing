package com.walkmind.extensions.netty

import io.netty.handler.stream.ChunkedWriteHandler
import reactor.core.Exceptions
import reactor.netty.Connection
import reactor.netty.NettyOutbound
import reactor.netty.NettyPipeline
import java.io.File
import java.net.URL
import java.nio.charset.Charset
import java.util.jar.JarFile

//fun NettyOutbound.sendFile2(url: URL): NettyOutbound {
//    return sendUsing(
//        { url.openStream() },
//        { c: Connection, fc ->
//            if (ReactorNetty.mustChunkFileTransfer(c, file)) {
//                ReactorNetty.addChunkedWriter(c)
//                try {
//                    val contentSize = getResourceSize(url) ?: error("Can't determine size of $url.")
//                    return@sendUsing ChunkedInputStream(fc, contentSize)
//                } catch (ioe: Exception) {
//                    throw Exceptions.propagate(ioe)
//                }
//            }
//            io.netty.channel.DefaultFileRegion(fc, position, count)
//        },
//        { it.close() }
//    )
//}

fun NettyOutbound.sendContentChunked(url: URL): NettyOutbound {
    return sendUsing(
        { url.openStream() },
        { c: Connection, fc ->

//            ReactorNetty.addChunkedWriter(c)
            if (c.channel().pipeline().get(ChunkedWriteHandler::class.java) == null)
                c.addHandlerLast(NettyPipeline.ChunkedWriter, ChunkedWriteHandler())

            try {

                val contentSize = getResourceSize(url) ?: error("Can't determine size of $url.")
                return@sendUsing ChunkedInputStream(fc, contentSize)
            } catch (ex: Exception) {
                throw Exceptions.propagate(ex)
            }
        },
        { it.close() }
    )
}

private fun getResourceSize(url: URL): Long? {
    return when (url.protocol) {
        "file" -> {
            val file = File(url.path.decodeURLPart())
            if (file.isFile) file.length() else null
        }
        "jar" -> {

            if (url.toString().startsWith("jar:file:")) {
                val jarPathSeparator = url.toString().indexOf("!", startIndex = 9)
                require(jarPathSeparator != -1) { "Jar path requires !/ separator but it is: $url" }

                val jarFile = JarFile(url.toString().substring(9, jarPathSeparator).decodeURLPart())
                val jarEntry = jarFile.getJarEntry(url.toString().substring(jarPathSeparator + 2))
                jarEntry?.size
            } else
                error("Invalid")
        }
//        "jrt" -> {
//            URIFileContent(url, mimeResolve(url.path.extension()))
//        }
        else -> null
    }
}

private fun String.decodeURLPart(start: Int = 0, end: Int = length, charset: Charset = Charsets.UTF_8): String = decodeScan(start, end, false, charset)

private fun String.decodeScan(start: Int, end: Int, plusIsSpace: Boolean, charset: Charset): String {
    for (index in start until end) {
        val ch = this[index]
        if (ch == '%' || (plusIsSpace && ch == '+'))
            return decodeImpl(start, end, index, plusIsSpace, charset)
    }
    return if (start == 0 && end == length) toString() else substring(start, end)
}

private fun CharSequence.decodeImpl(
    start: Int,
    end: Int,
    prefixEnd: Int,
    plusIsSpace: Boolean,
    charset: Charset
): String {

    val length = end - start
    // if length is big, it probably means it is encoded
    val sbSize = if (length > 255) length / 3 else length
    val sb = StringBuilder(sbSize)

    if (prefixEnd > start) {
        sb.append(this, start, prefixEnd)
    }

    var index = prefixEnd

    // reuse ByteArray for hex decoding stripes
    var bytes: ByteArray? = null

    while (index < end) {
        val c = this[index]
        when {
            plusIsSpace && c == '+' -> {
                sb.append(' ')
                index++
            }
            c == '%' -> {
                // if ByteArray was not needed before, create it with an estimate of remaining string be all hex
                if (bytes == null) {
                    bytes = ByteArray((end - index) / 3)
                }

                // fill ByteArray with all the bytes, so Charset can decode text
                var count = 0
                while (index < end && this[index] == '%') {
                    if (index + 2 >= end) {
                        throw RuntimeException(
                            "Incomplete trailing HEX escape: ${substring(index)}, in $this at $index"
                        )
                    }

                    val digit1 = charToHexDigit(this[index + 1])
                    val digit2 = charToHexDigit(this[index + 2])
                    if (digit1 == -1 || digit2 == -1) {
                        throw RuntimeException(
                            "Wrong HEX escape: %${this[index + 1]}${this[index + 2]}, in $this, at $index"
                        )
                    }

                    bytes[count++] = (digit1 * 16 + digit2).toByte()
                    index += 3
                }

                // Decode chars from bytes and put into StringBuilder
                // Note: Tried using ByteBuffer and using enc.decode() – it's slower
                sb.append(String(bytes, offset = 0, length = count, charset = charset))
            }
            else -> {
                sb.append(c)
                index++
            }
        }
    }

    return sb.toString()
}

private fun charToHexDigit(c2: Char) = when (c2) {
    in '0'..'9' -> c2 - '0'
    in 'A'..'F' -> c2 - 'A' + 10
    in 'a'..'f' -> c2 - 'a' + 10
    else -> -1
}
