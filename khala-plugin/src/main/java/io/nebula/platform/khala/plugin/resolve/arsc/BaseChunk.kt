package io.nebula.platform.khala.plugin.resolve.arsc

import java.nio.ByteBuffer

abstract class BaseChunk(var header: ChunkHeader) : SerializableResource {
    var offset = 0

    enum class Type(val code: Short) {
        NULL(0x0000),
        STRING_POOL(0x0001),
        TABLE(0x0002),
        XML(0x0003),
        XML_START_NAMESPACE(0x0100),
        XML_END_NAMESPACE(0x0101),
        XML_START_ELEMENT(0x0102),
        XML_END_ELEMENT(0x0103),
        XML_CDATA(0x0104),
        XML_RESOURCE_MAP(0x0180),
        TABLE_PACKAGE(0x0200),
        TABLE_TYPE(0x0201),
        TABLE_TYPE_SPEC(0x0202),
        TABLE_LIBRARY(0x0203);
    }

    companion object {
        fun create(buff: ByteBuffer): BaseChunk {
            val result: BaseChunk
            val header = ChunkHeader.create(buff)
            result = when (header.type) {
                Type.TABLE.code -> {
                    TableChunk(header)
                }
                Type.STRING_POOL.code -> {
                    StringPoolChunk(header)
                }
                else -> {
                    UnprocessedChunk(header)
                }
            }
            result.initContent(buff)
            return result
        }
    }

    fun initContent(buff: ByteBuffer) {
        offset = buff.position() - ChunkHeader.headerSize()
        init(buff)
    }

    abstract fun init(buff: ByteBuffer)

    protected fun writeHeader(buff: ByteBuffer) {
        buff.putShort(header.type)
        buff.putShort(header.headerSize)
        buff.putInt(totalSize())
//        buff.put(byteBuffer.array())
    }

    open fun headerSize() = header.headerSize

    open fun totalSize():Int {
        return 0
    }

}