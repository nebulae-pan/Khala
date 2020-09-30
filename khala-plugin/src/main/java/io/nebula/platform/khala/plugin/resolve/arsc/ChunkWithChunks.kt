package io.nebula.platform.khala.plugin.resolve.arsc

import java.nio.ByteBuffer

abstract class ChunkWithChunks(header: ChunkHeader) : BaseChunk(header) {
    var chunks: ArrayList<BaseChunk> = arrayListOf()

    override fun init(buff: ByteBuffer) {
        chunks.clear()
    }

    override fun totalSize(): Int {
        var size = 0
        for (chunk in chunks) {
            size += chunk.totalSize()
        }
        return size
    }
}