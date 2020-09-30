package io.nebula.platform.khala.plugin.resolve.arsc

import com.google.common.io.LittleEndianDataOutputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TableChunk(header: ChunkHeader) : ChunkWithChunks(header) {

    override fun init(buff: ByteBuffer) {
        var start = offset + header.headerSize
        val end = offset + header.getChunkSize()
        val packageCount = buff.int
        while (start < end) {
            val chunk = create(buff)
            chunks.add(chunk)
            start += chunk.header.size
        }

    }

    override fun toByteArray(): ByteArray {
        val header = ByteBuffer.allocate(headerSize().toInt())
        header.order(ByteOrder.LITTLE_ENDIAN)
        writeHeader(header)
        //write packageCount
        header.putInt(chunks.size - 1)

        val baos = ByteArrayOutputStream()

        LittleEndianDataOutputStream(baos).use { payload ->
            for (chunk in chunks) {
                payload.write(chunk.toByteArray())
            }
        }

        val payloadBytes = baos.toByteArray()


        val chunkSize = headerSize() + payloadBytes.size

        val buff = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        buff.put(header.array())
        buff.put(payloadBytes)
        buff.putInt(offset + 4, chunkSize)


        return buff.array()
    }
}
