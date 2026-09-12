package com.example.engine.dex

data class DisassemblyResult(
    val smaliCode: String,
    val branchCount: Int,
    val booleanConstCount: Int,
    val returnBoolean: Boolean,
    val invokedMethods: List<String>,
    val referencedStrings: List<String>,
    val referencedFields: List<String>,
    val callsEntitlement: Boolean,
    val accessesBilling: Boolean,
    val hasComparison: Boolean = false
)

class DexDisassembler(private val parser: DexParser) {

    fun disassemble(
        className: String,
        methodName: String,
        signature: String,
        returnType: String,
        accessFlags: Int,
        codeItem: DexCodeItem?
    ): DisassemblyResult {
        val flagsStr = formatAccessFlags(accessFlags)
        val sb = StringBuilder()
        sb.append(".method ").append(flagsStr).append(" ").append(signature).append("\n")

        if (codeItem == null) {
            sb.append("    # Native or Abstract method (No bytecode)\n")
            sb.append(".end method")
            return DisassemblyResult(
                smaliCode = sb.toString(),
                branchCount = 0,
                booleanConstCount = 0,
                returnBoolean = returnType == "Z",
                invokedMethods = emptyList(),
                referencedStrings = emptyList(),
                referencedFields = emptyList(),
                callsEntitlement = false,
                accessesBilling = false
            )
        }

        val locals = (codeItem.registersSize - codeItem.insSize).coerceAtLeast(0)
        sb.append("    .locals ").append(locals).append("\n\n")

        val insns = codeItem.insns
        val insnsSize = codeItem.insnsSize
        var idx = 0

        var branchCount = 0
        var booleanConstCount = 0
        var returnBoolean = returnType == "Z"
        val invokedMethods = mutableListOf<String>()
        val referencedStrings = mutableListOf<String>()
        val referencedFields = mutableListOf<String>()
        var callsEntitlement = false
        var accessesBilling = false
        var hasComparison = false

        // First pass: identify branch target labels
        val labels = HashMap<Int, String>()
        var labelCounter = 0
        var scanIdx = 0
        while (scanIdx < insnsSize) {
            val insn = insns[scanIdx].toInt() and 0xFFFF
            val opcode = insn and 0xFF
            val branchOffset = when (opcode) {
                0x28 -> (insn shr 8).toByte().toInt() // goto
                0x29 -> if (scanIdx + 1 < insnsSize) insns[scanIdx + 1].toInt() else 0 // goto/16
                in 0x32..0x3d -> if (scanIdx + 1 < insnsSize) insns[scanIdx + 1].toInt() else 0 // if-test
                else -> 0
            }
            if (branchOffset != 0) {
                val targetIdx = scanIdx + branchOffset
                if (targetIdx in 0 until insnsSize && !labels.containsKey(targetIdx)) {
                    labels[targetIdx] = ":cond_${labelCounter++}"
                }
            }
            val insnLength = getInstructionLength(opcode)
            scanIdx += insnLength.coerceAtLeast(1)
        }

        // Second pass: generate disassembly and analyze instructions
        idx = 0
        while (idx < insnsSize) {
            if (labels.containsKey(idx)) {
                sb.append("    ").append(labels[idx]).append("\n")
            }

            val insn = insns[idx].toInt() and 0xFFFF
            val opcode = insn and 0xFF

            when (opcode) {
                0x00 -> { // nop
                    sb.append("    nop\n")
                    idx += 1
                }

                0x01, 0x04, 0x07 -> { // move, move-wide, move-object
                    val vA = (insn shr 8) and 0x0F
                    val vB = (insn shr 12) and 0x0F
                    val name = when (opcode) {
                        0x04 -> "move-wide"
                        0x07 -> "move-object"
                        else -> "move"
                    }
                    sb.append("    $name v$vA, v$vB\n")
                    idx += 1
                }

                0x0a, 0x0b, 0x0c -> { // move-result, move-result-wide, move-result-object
                    val vA = (insn shr 8) and 0xFF
                    val name = when (opcode) {
                        0x0b -> "move-result-wide"
                        0x0c -> "move-result-object"
                        else -> "move-result"
                    }
                    sb.append("    $name v$vA\n")
                    idx += 1
                }

                0x0e -> { // return-void
                    sb.append("    return-void\n")
                    idx += 1
                }

                0x0f -> { // return
                    val vA = (insn shr 8) and 0xFF
                    sb.append("    return v$vA\n")
                    idx += 1
                }

                0x10 -> { // return-wide
                    val vA = (insn shr 8) and 0xFF
                    sb.append("    return-wide v$vA\n")
                    idx += 1
                }

                0x11 -> { // return-object
                    val vA = (insn shr 8) and 0xFF
                    sb.append("    return-object v$vA\n")
                    idx += 1
                }

                0x12 -> { // const/4 vA, #+B
                    val vA = (insn shr 8) and 0x0F
                    val vB = ((insn shr 12) shl 28) shr 28
                    sb.append("    const/4 v$vA, 0x${Integer.toHexString(vB and 0xF)}\n")
                    if (vB == 0 || vB == 1) {
                        booleanConstCount++
                    }
                    idx += 1
                }

                0x13 -> { // const/16 vA, #+BBBB
                    val vA = (insn shr 8) and 0xFF
                    val lit = if (idx + 1 < insnsSize) insns[idx + 1].toInt() else 0
                    sb.append("    const/16 v$vA, $lit\n")
                    idx += 2
                }

                0x14 -> { // const vA, #+BBBBBBBB
                    val vA = (insn shr 8) and 0xFF
                    val lit = if (idx + 2 < insnsSize) {
                        (insns[idx + 1].toInt() and 0xFFFF) or (insns[idx + 2].toInt() shl 16)
                    } else 0
                    sb.append("    const v$vA, 0x${Integer.toHexString(lit)}\n")
                    idx += 3
                }

                0x1a -> { // const-string vAA, string@BBBB
                    val vA = (insn shr 8) and 0xFF
                    val strIdx = if (idx + 1 < insnsSize) insns[idx + 1].toInt() and 0xFFFF else 0
                    val strVal = parser.getString(strIdx)
                    referencedStrings.add(strVal)
                    val safeStr = strVal.replace("\n", "\\n").replace("\"", "\\\"").take(60)
                    sb.append("    const-string v$vA, \"$safeStr\"\n")
                    idx += 2
                }

                0x1b -> { // const-string/jumbo vAA, string@BBBBBBBB
                    val vA = (insn shr 8) and 0xFF
                    val strIdx = if (idx + 2 < insnsSize) {
                        (insns[idx + 1].toInt() and 0xFFFF) or (insns[idx + 2].toInt() shl 16)
                    } else 0
                    val strVal = parser.getString(strIdx)
                    referencedStrings.add(strVal)
                    val safeStr = strVal.replace("\n", "\\n").replace("\"", "\\\"").take(60)
                    sb.append("    const-string/jumbo v$vA, \"$safeStr\"\n")
                    idx += 3
                }

                in 0x32..0x37 -> { // if-test vA, vB, +CCCC
                    branchCount++
                    hasComparison = true
                    val vA = (insn shr 8) and 0x0F
                    val vB = (insn shr 12) and 0x0F
                    val offset = if (idx + 1 < insnsSize) insns[idx + 1].toInt() else 0
                    val target = idx + offset
                    val label = labels[target] ?: ":label_$target"
                    val opName = when (opcode) {
                        0x32 -> "if-eq"
                        0x33 -> "if-ne"
                        0x34 -> "if-lt"
                        0x35 -> "if-ge"
                        0x36 -> "if-gt"
                        else -> "if-le"
                    }
                    sb.append("    $opName v$vA, v$vB, $label\n")
                    idx += 2
                }

                in 0x38..0x3d -> { // if-testz vAA, +BBBB
                    branchCount++
                    val vA = (insn shr 8) and 0xFF
                    val offset = if (idx + 1 < insnsSize) insns[idx + 1].toInt() else 0
                    val target = idx + offset
                    val label = labels[target] ?: ":label_$target"
                    val opName = when (opcode) {
                        0x38 -> "if-eqz"
                        0x39 -> "if-nez"
                        0x3a -> "if-ltz"
                        0x3b -> "if-gez"
                        0x3c -> "if-gtz"
                        else -> "if-lez"
                    }
                    sb.append("    $opName v$vA, $label\n")
                    idx += 2
                }

                0x28 -> { // goto +AA
                    val offset = (insn shr 8).toByte().toInt()
                    val target = idx + offset
                    val label = labels[target] ?: ":label_$target"
                    sb.append("    goto $label\n")
                    idx += 1
                }

                0x29 -> { // goto/16 +AAAA
                    val offset = if (idx + 1 < insnsSize) insns[idx + 1].toInt() else 0
                    val target = idx + offset
                    val label = labels[target] ?: ":label_$target"
                    sb.append("    goto/16 $label\n")
                    idx += 2
                }

                in 0x52..0x5f -> { // iget / iput
                    val vA = (insn shr 8) and 0x0F
                    val vB = (insn shr 12) and 0x0F
                    val fieldIdx = if (idx + 1 < insnsSize) insns[idx + 1].toInt() and 0xFFFF else 0
                    val fieldId = parser.getFieldId(fieldIdx)
                    val fieldName = if (fieldId != null) parser.getString(fieldId.nameIdx) else "field@$fieldIdx"
                    referencedFields.add(fieldName)
                    val opName = when (opcode) {
                        0x52 -> "iget"
                        0x53 -> "iget-wide"
                        0x54 -> "iget-object"
                        0x55 -> "iget-boolean"
                        0x56 -> "iget-byte"
                        0x57 -> "iget-char"
                        0x58 -> "iget-short"
                        0x59 -> "iput"
                        0x5a -> "iput-wide"
                        0x5b -> "iput-object"
                        0x5c -> "iput-boolean"
                        0x5d -> "iput-byte"
                        0x5e -> "iput-char"
                        else -> "iput-short"
                    }
                    sb.append("    $opName v$vA, v$vB, $fieldName\n")
                    idx += 2
                }

                in 0x60..0x6d -> { // sget / sput
                    val vA = (insn shr 8) and 0xFF
                    val fieldIdx = if (idx + 1 < insnsSize) insns[idx + 1].toInt() and 0xFFFF else 0
                    val fieldId = parser.getFieldId(fieldIdx)
                    val fieldName = if (fieldId != null) parser.getString(fieldId.nameIdx) else "field@$fieldIdx"
                    referencedFields.add(fieldName)
                    val opName = when (opcode) {
                        0x60 -> "sget"
                        0x61 -> "sget-wide"
                        0x62 -> "sget-object"
                        0x63 -> "sget-boolean"
                        0x64 -> "sget-byte"
                        0x65 -> "sget-char"
                        0x66 -> "sget-short"
                        0x67 -> "sput"
                        0x68 -> "sput-wide"
                        0x69 -> "sput-object"
                        0x6a -> "sput-boolean"
                        0x6b -> "sput-byte"
                        0x6c -> "sput-char"
                        else -> "sput-short"
                    }
                    sb.append("    $opName v$vA, $fieldName\n")
                    idx += 2
                }

                in 0x6e..0x72 -> { // invoke-kind {vC, vD, vE, vF, vG}, meth@BBBB
                    val methodIdx = if (idx + 1 < insnsSize) insns[idx + 1].toInt() and 0xFFFF else 0
                    val targetSig = parser.getMethodSignature(methodIdx)
                    val methodId = parser.getMethodId(methodIdx)
                    val targetClass = if (methodId != null) parser.getType(methodId.classIdx) else ""

                    invokedMethods.add(targetSig)
                    if (isEntitlementRelated(targetSig) || isEntitlementRelated(targetClass)) {
                        callsEntitlement = true
                    }
                    if (targetClass.contains("billingclient") || targetSig.contains("queryPurchases") || targetSig.contains("BillingClient")) {
                        accessesBilling = true
                    }

                    val opName = when (opcode) {
                        0x6e -> "invoke-virtual"
                        0x6f -> "invoke-super"
                        0x70 -> "invoke-direct"
                        0x71 -> "invoke-static"
                        else -> "invoke-interface"
                    }
                    sb.append("    $opName { ... }, $targetClass->$targetSig\n")
                    idx += 3
                }

                in 0x74..0x78 -> { // invoke-kind/range {vCCCC .. vNNNN}, meth@BBBB
                    val methodIdx = if (idx + 1 < insnsSize) insns[idx + 1].toInt() and 0xFFFF else 0
                    val targetSig = parser.getMethodSignature(methodIdx)
                    val methodId = parser.getMethodId(methodIdx)
                    val targetClass = if (methodId != null) parser.getType(methodId.classIdx) else ""

                    invokedMethods.add(targetSig)
                    if (isEntitlementRelated(targetSig) || isEntitlementRelated(targetClass)) {
                        callsEntitlement = true
                    }
                    if (targetClass.contains("billingclient") || targetSig.contains("queryPurchases")) {
                        accessesBilling = true
                    }

                    val opName = when (opcode) {
                        0x74 -> "invoke-virtual/range"
                        0x75 -> "invoke-super/range"
                        0x76 -> "invoke-direct/range"
                        0x77 -> "invoke-static/range"
                        else -> "invoke-interface/range"
                    }
                    sb.append("    $opName { ... }, $targetClass->$targetSig\n")
                    idx += 3
                }

                else -> { // Fallback for other instructions
                    val length = getInstructionLength(opcode).coerceAtLeast(1)
                    sb.append("    # opcode 0x${Integer.toHexString(opcode)}\n")
                    idx += length
                }
            }
        }

        sb.append(".end method")

        return DisassemblyResult(
            smaliCode = sb.toString(),
            branchCount = branchCount,
            booleanConstCount = booleanConstCount,
            returnBoolean = returnBoolean,
            invokedMethods = invokedMethods,
            referencedStrings = referencedStrings,
            referencedFields = referencedFields,
            callsEntitlement = callsEntitlement,
            accessesBilling = accessesBilling,
            hasComparison = hasComparison
        )
    }

    private fun isEntitlementRelated(target: String): Boolean {
        val lower = target.lowercase()
        return lower.contains("premium") ||
                lower.contains("vip") ||
                lower.contains("ispremium") ||
                lower.contains("ispro") ||
                lower.contains("hasaccess") ||
                lower.contains("isactive") ||
                lower.contains("checklicense") ||
                lower.contains("entitlement") ||
                lower.contains("isvip")
    }

    private fun getInstructionLength(opcode: Int): Int {
        return when (opcode) {
            0x00, 0x01, 0x04, 0x07, 0x0a, 0x0b, 0x0c, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x28 -> 1
            0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x21, 0x22, 0x27, 0x29,
            in 0x32..0x3d, in 0x44..0x5f, in 0x60..0x6d, in 0x7b..0x8f, in 0x90..0xaf, in 0xd0..0xe2 -> 2
            0x03, 0x06, 0x09, 0x14, 0x17, 0x18, 0x1b, 0x24, 0x25, 0x26, 0x2a, 0x2b, 0x2c,
            in 0x6e..0x72, in 0x74..0x78, in 0xfc..0xff -> 3
            0x23, 0x30, 0x31 -> 4
            else -> 1
        }
    }

    companion object {
        fun formatAccessFlags(flags: Int): String {
            val list = mutableListOf<String>()
            if ((flags and 0x0001) != 0) list.add("public")
            if ((flags and 0x0002) != 0) list.add("private")
            if ((flags and 0x0004) != 0) list.add("protected")
            if ((flags and 0x0008) != 0) list.add("static")
            if ((flags and 0x0010) != 0) list.add("final")
            if ((flags and 0x0020) != 0) list.add("synchronized")
            if ((flags and 0x0100) != 0) list.add("native")
            if ((flags and 0x0400) != 0) list.add("abstract")
            return list.joinToString(" ")
        }
    }
}
