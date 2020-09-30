package io.nebula.platform.khala.plugin.resolve.arsc

import io.nebula.platform.khala.plugin.resolve.arsc.BaseChunk
import io.nebula.platform.khala.plugin.resolve.arsc.SerializableResource
import com.google.common.io.ByteStreams
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ArscFile(bytes: ByteArray) : SerializableResource {
    var chunks = arrayListOf<SerializableResource>()
    private var buff: ByteBuffer

    init {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buff = buffer
        while (buff.remaining() > 0) {
            val chunk = BaseChunk.create(buff)
            chunks.add(chunk)
        }
    }

    companion object {
        fun fromFile(file: File): ArscFile {
            val fis = FileInputStream(file)
            val byteArray = ByteStreams.toByteArray(fis)
            return ArscFile(byteArray)
        }
    }

    override fun toByteArray(): ByteArray {
        val output = ByteStreams.newDataOutput()
        for (chunk in chunks) {
            output.write(chunk.toByteArray())
        }
        return output.toByteArray()
    }

}