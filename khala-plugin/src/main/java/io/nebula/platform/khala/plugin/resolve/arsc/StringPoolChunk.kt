package io.nebula.platform.khala.plugin.resolve.arsc

import com.google.common.io.LittleEndianDataOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class StringPoolChunk(header: ChunkHeader) : BaseChunk(header) {
    var stringCount: Int = 0
    var styleCount: Int = 0
    var flags: Int = 0
    var stringStart: Int = 0
    var styleStart: Int = 0

    var isOriginalDeduped = false
    var chunkSize = 0

    // These are the defined flags for the "flags" field of ResourceStringPoolHeader
    private val SORTED_FLAG = 1 shl 0
    private val UTF8_FLAG = 1 shl 8

    val strings = arrayListOf<String>()
    val styles = arrayListOf<StringPoolStyle>()

    override fun init(buff: ByteBuffer) {
        stringCount = buff.int
        styleCount = buff.int
        flags = buff.int
        stringStart = buff.int
        styleStart = buff.int
        strings.clear()
        strings.addAll(readStrings(buff, offset + stringStart, stringCount))
        styles.clear()
        styles.addAll(readStyles(buff, offset + styleStart, styleCount))
        buff.position(offset + header.size)
    }

    fun getString(index: Int):String {
        return strings[index]
    }

    fun setString(index: Int, str:String) {
        strings[index] = str
    }

    private fun readStyles(buffer: ByteBuffer, offset: Int, count: Int): List<StringPoolStyle> {
        val result = ArrayList<StringPoolStyle>()
        for (i in 0 until count) {
            val styleOffset = offset + buffer.int
            result.add(StringPoolStyle.create(buffer, styleOffset))
        }
        return result
    }

    private fun readStrings(buff: ByteBuffer, offset: Int, count: Int): List<String> {
        val result = arrayListOf<String>()
        var preOffset = -1
        for (i in 0 until count) {
            val stringOffset = offset + buff.int
            result.add(ResourceString.decodeString(buff, stringOffset, getStringType()))
            if (stringOffset < preOffset) {
                isOriginalDeduped = true
            }
            preOffset = stringOffset
        }
        return result
    }

    private fun getStringType(): ResourceString.Type {
        return if (isUTF8()) ResourceString.Type.UTF8 else ResourceString.Type.UTF16
    }

    private fun isUTF8(): Boolean {
        return flags and UTF8_FLAG != 0
    }

    @Throws(IOException::class)
    private fun writeStrings(payload: DataOutput, offsets: ByteBuffer): Int {
        var stringOffset = 0
        val used = HashMap<String, Int>()  // Keeps track of strings already written
        for (string in strings) {
            // Dedupe everything except stylized strings, unless shrink is true (then dedupe everything)
            if (used.containsKey(string) && isOriginalDeduped) {
                val offset = used[string]
                offsets.putInt(offset ?: 0)
            } else {
                val encodedString = ResourceString.encodeString(string, getStringType())
                payload.write(encodedString)
                used[string] = stringOffset
                offsets.putInt(stringOffset)
                stringOffset += encodedString.size
            }
        }

        // ARSC files pad to a 4-byte boundary. We should do so too.
        stringOffset = writePad(payload, stringOffset)
        return stringOffset
    }

    @Throws(IOException::class)
    private fun writeStyles(payload: DataOutput, offsets: ByteBuffer): Int {
        var styleOffset = 0
        if (styles.size > 0) {
            val used = HashMap<StringPoolStyle, Int>()  // Keeps track of bytes already written
            for (style in styles) {
                if (!used.containsKey(style)) {
                    val encodedStyle = style.toByteArray()
                    payload.write(encodedStyle)
                    used[style] = styleOffset
                    offsets.putInt(styleOffset)
                    styleOffset += encodedStyle.size
                } else {  // contains key and shrink is true
                    val offset = used[style]
                    offsets.putInt(offset ?: 0)
                }
            }
            // The end of the spans are terminated with another sentinel value
            payload.writeInt(StringPoolStyle.RES_STRING_POOL_SPAN_END)
            styleOffset += 4
            // TODO(acornwall): There appears to be an extra SPAN_END here... why?
            payload.writeInt(StringPoolStyle.RES_STRING_POOL_SPAN_END)
            styleOffset += 4

            styleOffset = writePad(payload, styleOffset)
        }
        return styleOffset
    }

    @Throws(IOException::class)
    private fun writePad(output: DataOutput, currentLength: Int): Int {
        var length = currentLength
        while (length % 4 != 0) {
            output.write(0)
            ++length
        }
        return length
    }

    @Throws(IOException::class)
    private fun writePayload(output: DataOutput, header: ByteBuffer) {
        val baos = ByteArrayOutputStream()
        var stringOffset = 0
        val offsets = ByteBuffer.allocate(getOffsetSize())
        offsets.order(ByteOrder.LITTLE_ENDIAN)

        // Write to a temporary payload so we can rearrange this and put the offsets first
        LittleEndianDataOutputStream(baos).use { payload ->
            stringOffset = writeStrings(payload, offsets)
            writeStyles(payload, offsets)
        }

        output.write(offsets.array())
        output.write(baos.toByteArray())
        if (styles.isNotEmpty()) {
            header.putInt(STYLE_START_OFFSET, headerSize() + getOffsetSize() + stringOffset)
        }
    }

    private fun getOffsetSize(): Int {
        return (strings.size + styles.size) * 4
    }

    private val CHUNK_SIZE_OFFSET = 4
    private val STYLE_START_OFFSET = 24

    override fun totalSize(): Int {
        return chunkSize
    }

    override fun toByteArray(): ByteArray {

        val header = ByteBuffer.allocate(headerSize().toInt()).order(ByteOrder.LITTLE_ENDIAN)
        writeHeader(header)  // The chunk size isn't known yet. This will be filled in later.
        header.putInt(strings.size)
        header.putInt(styles.size)
        header.putInt(flags)
        header.putInt(headerSize() + getOffsetSize())
        header.putInt(0)

        val baos = ByteArrayOutputStream()

        LittleEndianDataOutputStream(baos).use { payload -> writePayload(payload, header) }

        val payloadBytes = baos.toByteArray()
        chunkSize = headerSize() + payloadBytes.size

        header.putInt(CHUNK_SIZE_OFFSET, chunkSize)

        // Combine results
        val result = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        result.put(header.array())
        result.put(payloadBytes)
        return result.array()

    }

    class StringPoolStyle(val spans: ArrayList<StringPoolSpan>) : SerializableResource {

        companion object {
            val RES_STRING_POOL_SPAN_END: Int = -0x1

            fun create(buffer: ByteBuffer, off: Int): StringPoolStyle {
                var offset = off
                val spans = arrayListOf<StringPoolSpan>()
                var nameIndex = buffer.getInt(offset)
                while (nameIndex != RES_STRING_POOL_SPAN_END) {
                    spans.add(StringPoolSpan.create(buffer, offset))
                    offset += StringPoolSpan.size()
                    nameIndex = buffer.getInt(offset)
                }
                return StringPoolStyle(spans)
            }
        }


        override fun toByteArray(): ByteArray {
            val baos = ByteArrayOutputStream()
            LittleEndianDataOutputStream(baos).use { payload ->
                for (span in spans) {
                    val encodedSpan = span.toByteArray()
                    if (encodedSpan.size != StringPoolSpan.size()) {
                        throw IllegalStateException("Encountered a span of invalid length.")
                    }
                    payload.write(encodedSpan)
                }
                payload.writeInt(RES_STRING_POOL_SPAN_END)
            }
            return baos.toByteArray()
        }
    }

    class StringPoolSpan(var nameIndex: Int, var first: Int, var last: Int) : SerializableResource {
        companion object {
            fun create(buffer: ByteBuffer, offset: Int): StringPoolSpan {
                val nameIndex = buffer.getInt(offset)
                val start = buffer.getInt(offset + 4)
                val stop = buffer.getInt(offset + 8)
                return StringPoolSpan(nameIndex, start, stop)
            }

            fun size() = 12
        }

        override fun toByteArray(): ByteArray {
            val buffer = ByteBuffer.allocate(size()).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(nameIndex)
            buffer.putInt(first)
            buffer.putInt(last)
            return buffer.array()
        }

    }

}