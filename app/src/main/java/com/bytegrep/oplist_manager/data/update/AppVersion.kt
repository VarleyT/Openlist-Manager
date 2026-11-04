package com.bytegrep.oplist_manager.data.update

/**
 * 语义化版本（MAJOR.MINOR.PATCH[-prerelease][+build]）的最小实现，用于检查更新。
 * 允许前缀 v / V，缺失的数字段按 0 处理（如 "1.2" == "1.2.0"）。
 */
data class AppVersion(
    val numbers: List<Int>,
    val preRelease: String?,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(numbers.size, other.numbers.size)
        for (i in 0 until size) {
            val diff = numbers.getOrElse(i) { 0 }.compareTo(other.numbers.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return when {
            preRelease == other.preRelease -> 0
            // 正式版高于同号预发布版
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> comparePreRelease(preRelease, other.preRelease)
        }
    }

    companion object {
        private val NUMBERS = Regex("""^\d+(\.\d+)*$""")

        /** 解析失败返回 null */
        fun parse(raw: String): AppVersion? {
            val core = raw.trim().removePrefix("v").removePrefix("V").substringBefore('+')
            val numberPart = core.substringBefore('-')
            if (!NUMBERS.matches(numberPart)) return null
            val numbers = numberPart.split('.').map { it.toIntOrNull() ?: return null }
            val preRelease = core.substringAfter('-', missingDelimiterValue = "").ifEmpty { null }
            return AppVersion(numbers, preRelease)
        }

        /** 按 SemVer 规则逐段比较预发布标识：纯数字按数值，否则按字典序，数字段低于字母段 */
        private fun comparePreRelease(a: String, b: String): Int {
            val left = a.split('.')
            val right = b.split('.')
            for (i in 0 until minOf(left.size, right.size)) {
                val l = left[i]
                val r = right[i]
                val ln = l.toIntOrNull()
                val rn = r.toIntOrNull()
                val diff = when {
                    ln != null && rn != null -> ln.compareTo(rn)
                    ln != null -> -1
                    rn != null -> 1
                    else -> l.compareTo(r)
                }
                if (diff != 0) return diff
            }
            return left.size.compareTo(right.size)
        }
    }
}

/** [latest] 是否比 [current] 新；任一无法解析时视为无更新 */
fun isNewerVersion(latest: String, current: String): Boolean {
    val l = AppVersion.parse(latest) ?: return false
    val c = AppVersion.parse(current) ?: return false
    return l > c
}
