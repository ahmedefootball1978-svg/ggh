package com.example.engine.dex

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class DexHeader(
    val magic: String,
    val fileSize: Long,
    val headerSize: Int,
    val endianTag: Int,
    val stringIdsSize: Int,
    val stringIdsOff: Long,
    val typeIdsSize: Int,
    val typeIdsOff: Long,
    val protoIdsSize: Int,
    val protoIdsOff: Long,
    val fieldIdsSize: Int,
    val fieldIdsOff: Long,
    val methodIdsSize: Int,
    val methodIdsOff: Long,
    val classDefsSize: Int,
    val classDefsOff: Long,
    val dataSize: Long,
    val dataOff: Long
)

data class DexMethodId(
    val methodIdx: Int,
    val classIdx: Int,
    val protoIdx: Int,
    val nameIdx: Int
)

data class DexProtoId(
    val protoIdx: Int,
    val shortyIdx: Int,
    val returnTypeIdx: Int,
    val parametersOff: Long
)

data class DexFieldId(
    val fieldIdx: Int,
    val classIdx: Int,
    val typeIdx: Int,
    val nameIdx: Int
)

data class DexClassDef(
    val classDefIdx: Int,
    val classIdx: Int,
    val accessFlags: Int,
    val superclassIdx: Int,
    val classDataOff: Long
)

data class EncodedMethod(
    val methodIdx: Int,
    val accessFlags: Int,
    val codeOff: Long
)

data class DexCodeItem(
    val registersSize: Int,
    val insSize: Int,
    val outsSize: Int,
    val triesSize: Int,
    val debugInfoOff: Long,
    val insnsSize: Int,
    val insns: ShortArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DexCodeItem) return false
        return registersSize == other.registersSize &&
                insSize == other.insSize &&
                codeOffsetMatches(other)
    }

    private fun codeOffsetMatches(other: DexCodeItem): Boolean {
        return insnsSize == other.insnsSize && insns.contentEquals(other.insns)
    }

    override fun hashCode(): Int {
        var result = registersSize
        result = 31 * result + insSize
        result = 31 * result + insnsSize
        return result
    }
}

class DexParser(
    val dexFile: File,
    val dexName: String
) : Closeable {

    private val raf = RandomAccessFile(dexFile, "r")
    private val channel: FileChannel = raf.channel
    private val mappedBuffer: ByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, dexFile.length()).apply {
        order(ByteOrder.LITTLE_ENDIAN)
    }

    val header: DexHeader

    // Small cache for decoded strings to prevent repeated decoding
    private val stringCache = HashMap<Int, String>(1024)
    private val typeCache = HashMap<Int, String>(512)

    init {
        header = readHeader()
    }

    private fun readHeader(): DexHeader {
        mappedBuffer.position(0)
        val magicBytes = ByteArray(8)
        mappedBuffer.get(magicBytes)
        val magic = String(magicBytes)

        if (!magic.startsWith("dex\n")) {
            throw IllegalArgumentException("Invalid DEX magic in $dexName: $magic")
        }

        mappedBuffer.position(32) // Skip checksum (4) + signature (20)
        val fileSize = mappedBuffer.int.toLong() and 0xFFFFFFFFL
        val headerSize = mappedBuffer.int
        val endianTag = mappedBuffer.int
        mappedBuffer.position(mappedBuffer.position() + 8) // link_size, link_off
        val mapOff = mappedBuffer.int.toLong() and 0xFFFFFFFFL

        val stringIdsSize = mappedBuffer.int
        val stringIdsOff = mappedBuffer.int.toLong() and 0xFFFFFFFFL
        val typeIdsSize = mappedBuffer.int
        val typeIdsOff = mappedBuffer.int.toLong() and 0xFFFFFFFFL
        val protoIdsSize = mappedBuffer.int
        val protoIdsOff = mappedBuffer.int.toLong() and 0xFFFFFFFFL
        val fieldIdsSize = mappedBuffer.int
        val fieldIdsOff = mappedBuffer.int.toLong() and 0xFFFFFFFFL
        val methodIdsSize = mappedBuffer.int
        val methodIdsOff = mappedBuffer.int.toLong() and 0xFFFFFFFFL
        val classDefsSize = mappedBuffer.int
        val classDefsOff = mappedBuffer.int.toLong() and 0xFFFFFFFFL
        val dataSize = mappedBuffer.int.toLong() and 0xFFFFFFFFL
        val dataOff = mappedBuffer.int.toLong() and 0xFFFFFFFFL

        return DexHeader(
            magic = magic,
            fileSize = fileSize,
            headerSize = headerSize,
            endianTag = endianTag,
            stringIdsSize = stringIdsSize,
            stringIdsOff = stringIdsOff,
            typeIdsSize = typeIdsSize,
            typeIdsOff = typeIdsOff,
            protoIdsSize = protoIdsSize,
            protoIdsOff = protoIdsOff,
            fieldIdsSize = fieldIdsSize,
            fieldIdsOff = fieldIdsOff,
            methodIdsSize = methodIdsSize,
            methodIdsOff = methodIdsOff,
            classDefsSize = classDefsSize,
            classDefsOff = classDefsOff,
            dataSize = dataSize,
            dataOff = dataOff
        )
    }

    fun getString(stringIdx: Int): String {
        if (stringIdx < 0 || stringIdx >= header.stringIdsSize) return ""
        val cached = stringCache[stringIdx]
        if (cached != null) return cached

        val offsetPos = (header.stringIdsOff + stringIdx * 4).toInt()
        val stringDataOff = mappedBuffer.getInt(offsetPos)

        val tempBuf = mappedBuffer.duplicate()
        tempBuf.position(stringDataOff)

        // Read utf16_size (uleb128)
        readUleb128(tempBuf)

        // Read MUTF-8 bytes until 0x00
        val bytes = ArrayList<Byte>(32)
        while (tempBuf.hasRemaining()) {
            val b = tempBuf.get()
            if (b == 0.toByte()) break
            bytes.add(b)
        }

        val str = try {
            String(bytes.toByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }

        if (stringCache.size < 4096) {
            stringCache[stringIdx] = str
        }
        return str
    }

    fun getType(typeIdx: Int): String {
        if (typeIdx < 0 || typeIdx >= header.typeIdsSize) return ""
        val cached = typeCache[typeIdx]
        if (cached != null) return cached

        val offsetPos = (header.typeIdsOff + typeIdx * 4).toInt()
        val descriptorIdx = mappedBuffer.getInt(offsetPos)
        val typeStr = getString(descriptorIdx)

        if (typeCache.size < 2048) {
            typeCache[typeIdx] = typeStr
        }
        return typeStr
    }

    fun getMethodId(methodIdx: Int): DexMethodId? {
        if (methodIdx < 0 || methodIdx >= header.methodIdsSize) return null
        val offsetPos = (header.methodIdsOff + methodIdx * 8).toInt()
        val classIdx = mappedBuffer.getShort(offsetPos).toInt() and 0xFFFF
        val protoIdx = mappedBuffer.getShort(offsetPos + 2).toInt() and 0xFFFF
        val nameIdx = mappedBuffer.getInt(offsetPos + 4)

        return DexMethodId(
            methodIdx = methodIdx,
            classIdx = classIdx,
            protoIdx = protoIdx,
            nameIdx = nameIdx
        )
    }

    fun getProtoId(protoIdx: Int): DexProtoId? {
        if (protoIdx < 0 || protoIdx >= header.protoIdsSize) return null
        val offsetPos = (header.protoIdsOff + protoIdx * 12).toInt()
        val shortyIdx = mappedBuffer.getInt(offsetPos)
        val returnTypeIdx = mappedBuffer.getInt(offsetPos + 4)
        val parametersOff = mappedBuffer.getInt(offsetPos + 8).toLong() and 0xFFFFFFFFL

        return DexProtoId(
            protoIdx = protoIdx,
            shortyIdx = shortyIdx,
            returnTypeIdx = returnTypeIdx,
            parametersOff = parametersOff
        )
    }

    fun getFieldId(fieldIdx: Int): DexFieldId? {
        if (fieldIdx < 0 || fieldIdx >= header.fieldIdsSize) return null
        val offsetPos = (header.fieldIdsOff + fieldIdx * 8).toInt()
        val classIdx = mappedBuffer.getShort(offsetPos).toInt() and 0xFFFF
        val typeIdx = mappedBuffer.getShort(offsetPos + 2).toInt() and 0xFFFF
        val nameIdx = mappedBuffer.getInt(offsetPos + 4)

        return DexFieldId(
            fieldIdx = fieldIdx,
            classIdx = classIdx,
            typeIdx = typeIdx,
            nameIdx = nameIdx
        )
    }

    fun getMethodSignature(methodIdx: Int): String {
        val methodId = getMethodId(methodIdx) ?: return ""
        val methodName = getString(methodId.nameIdx)
        val protoId = getProtoId(methodId.protoIdx) ?: return "$methodName()"
        val returnType = getType(protoId.returnTypeIdx)
        val params = getProtoParameters(protoId.parametersOff)
        return "$methodName($params)$returnType"
    }

    fun getProtoParameters(parametersOff: Long): String {
        if (parametersOff <= 0 || parametersOff >= mappedBuffer.capacity()) return ""
        val tempBuf = mappedBuffer.duplicate()
        tempBuf.position(parametersOff.toInt())
        val size = tempBuf.int
        if (size <= 0 || size > 256) return ""

        val sb = StringBuilder()
        for (i in 0 until size) {
            val typeIdx = tempBuf.short.toInt() and 0xFFFF
            sb.append(getType(typeIdx))
        }
        return sb.toString()
    }

    fun getClassDef(classDefIdx: Int): DexClassDef? {
        if (classDefIdx < 0 || classDefIdx >= header.classDefsSize) return null
        val offsetPos = (header.classDefsOff + classDefIdx * 32).toInt()
        val classIdx = mappedBuffer.getInt(offsetPos)
        val accessFlags = mappedBuffer.getInt(offsetPos + 4)
        val superclassIdx = mappedBuffer.getInt(offsetPos + 8)
        val classDataOff = mappedBuffer.getInt(offsetPos + 24).toLong() and 0xFFFFFFFFL

        return DexClassDef(
            classDefIdx = classDefIdx,
            classIdx = classIdx,
            accessFlags = accessFlags,
            superclassIdx = superclassIdx,
            classDataOff = classDataOff
        )
    }

    /**
     * Parses the methods belonging to a class given its classDataOff.
     */
    fun getClassMethods(classDataOff: Long): List<EncodedMethod> {
        if (classDataOff <= 0 || classDataOff >= mappedBuffer.capacity()) return emptyList()

        val tempBuf = mappedBuffer.duplicate()
        tempBuf.position(classDataOff.toInt())

        val staticFieldsSize = readUleb128(tempBuf)
        val instanceFieldsSize = readUleb128(tempBuf)
        val directMethodsSize = readUleb128(tempBuf)
        val virtualMethodsSize = readUleb128(tempBuf)

        // Skip static fields
        for (i in 0 until staticFieldsSize) {
            readUleb128(tempBuf) // field_idx_diff
            readUleb128(tempBuf) // access_flags
        }

        // Skip instance fields
        for (i in 0 until instanceFieldsSize) {
            readUleb128(tempBuf) // field_idx_diff
            readUleb128(tempBuf) // access_flags
        }

        val methods = ArrayList<EncodedMethod>(directMethodsSize + virtualMethodsSize)

        // Read direct methods
        var methodIdx = 0
        for (i in 0 until directMethodsSize) {
            val diff = readUleb128(tempBuf)
            val flags = readUleb128(tempBuf)
            val codeOff = readUleb128(tempBuf).toLong() and 0xFFFFFFFFL
            methodIdx += diff
            methods.add(EncodedMethod(methodIdx, flags, codeOff))
        }

        // Read virtual methods
        methodIdx = 0
        for (i in 0 until virtualMethodsSize) {
            val diff = readUleb128(tempBuf)
            val flags = readUleb128(tempBuf)
            val codeOff = readUleb128(tempBuf).toLong() and 0xFFFFFFFFL
            methodIdx += diff
            methods.add(EncodedMethod(methodIdx, flags, codeOff))
        }

        return methods
    }

    /**
     * Reads a code item from codeOff.
     */
    fun getCodeItem(codeOff: Long): DexCodeItem? {
        if (codeOff <= 0 || codeOff >= mappedBuffer.capacity()) return null

        val tempBuf = mappedBuffer.duplicate()
        tempBuf.position(codeOff.toInt())

        val registersSize = tempBuf.short.toInt() and 0xFFFF
        val insSize = tempBuf.short.toInt() and 0xFFFF
        val outsSize = tempBuf.short.toInt() and 0xFFFF
        val triesSize = tempBuf.short.toInt() and 0xFFFF
        val debugInfoOff = tempBuf.int.toLong() and 0xFFFFFFFFL
        val insnsSize = tempBuf.int

        if (insnsSize < 0 || insnsSize > 250000) return null

        val insns = ShortArray(insnsSize)
        for (i in 0 until insnsSize) {
            insns[i] = tempBuf.short
        }

        return DexCodeItem(
            registersSize = registersSize,
            insSize = insSize,
            outsSize = outsSize,
            triesSize = triesSize,
            debugInfoOff = debugInfoOff,
            insnsSize = insnsSize,
            insns = insns
        )
    }

    override fun close() {
        try {
            channel.close()
            raf.close()
        } catch (_: Exception) {}
    }

    companion object {
        fun readUleb128(buffer: ByteBuffer): Int {
            var result = 0
            var cur: Int
            var count = 0
            do {
                if (!buffer.hasRemaining()) break
                cur = buffer.get().toInt() and 0xFF
                result = result or ((cur and 0x7F) shl (count * 7))
                count++
            } while ((cur and 0x80) != 0 && count < 5)
            return result
        }
    }
}
