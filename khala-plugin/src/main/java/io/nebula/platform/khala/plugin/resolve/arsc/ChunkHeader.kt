package io.nebula.platform.khala.plugin.resolve.arsc

import java.nio.ByteBuffer

class ChunkHeader(val type: Short, var headerSize: Short, var size: Int) {

    companion object {
        fun create(buff: ByteBuffer): ChunkHeader {
            val type = buff.short
            val headerSize = buff.short
            val size = buff.int
            return ChunkHeader(type, headerSize, size)
        }

        fun headerSize() = 8
    }

    fun getChunkSize(): Int {
        return size
    }

    override fun toString(): String {
        return "ChunkHeader(type=$type, headerSize=$headerSize, size=$size)"
    }
}