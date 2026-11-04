package com.bytegrep.oplist_manager.data.player

/** 同目录连续播放列表（完整路径，按自然顺序） */
data class Playlist(val paths: List<String>, val index: Int) {
    val current: String? get() = paths.getOrNull(index)
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = index in 0 until paths.lastIndex

    fun moveTo(path: String): Playlist = copy(index = paths.indexOf(path))
    fun next(): Playlist? = if (hasNext) copy(index = index + 1) else null
    fun previous(): Playlist? = if (hasPrevious) copy(index = index - 1) else null

    companion object {
        fun single(path: String) = Playlist(listOf(path), 0)
    }
}

/**
 * 自然排序：数字段按数值比较，使「第2集」排在「第10集」之前；其余按忽略大小写比较。
 */
object NaturalOrder : Comparator<String> {
    private val CHUNK = Regex("""\d+|\D+""")

    override fun compare(a: String, b: String): Int {
        val left = CHUNK.findAll(a).map { it.value }.toList()
        val right = CHUNK.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(left.size, right.size)) {
            val l = left[i]
            val r = right[i]
            val diff = if (l[0].isDigit() && r[0].isDigit()) {
                compareNumeric(l, r)
            } else {
                l.compareTo(r, ignoreCase = true)
            }
            if (diff != 0) return diff
        }
        return left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b)
    }

    /** 不转数值，避免超长数字溢出：先比去前导零后的长度，再按字典序 */
    private fun compareNumeric(l: String, r: String): Int {
        val ln = l.trimStart('0')
        val rn = r.trimStart('0')
        return if (ln.length != rn.length) ln.length - rn.length else ln.compareTo(rn)
    }
}
