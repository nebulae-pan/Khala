package io.nebula.platform.khala.plugin.resolve.arsc

import java.nio.ByteBuffer
import java.nio.ByteOrder

class UnprocessedChunk(header: ChunkHeader) : BaseChunk(header) {

    lateinit var bytes: ByteArray

    override fun init(buff: ByteBuffer) {
        bytes = ByteArray(header.size - 8)
        val start = 8
        var i = 0
        while (i < header.size - start) {
            bytes[i++] = buff.get()
        }
    }

    override fun toByteArray(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(totalSize())
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        writeHeader(byteBuffer)

        byteBuffer.put(bytes)
        return byteBuffer.array()
    }

    override fun totalSize(): Int {
        return 8 + bytes.size
    }

}