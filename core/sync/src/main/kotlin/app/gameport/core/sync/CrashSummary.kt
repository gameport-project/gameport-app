package app.gameport.core.sync

/**
 * Reads Android's tombstone (the protobuf `crash-tombstone.pb` the system writes for a native crash) and tells in words what a person needs first: the signal, the
 * address that faulted, the thread that crashed and the top of its call stack, with the library and the function of each step when the library has symbols.
 * Only what is needed is read (a few fields, by their number in Android's `tombstone.proto`); anything unreadable ends the summary instead of failing it.
 */
object CrashSummary {
    private const val MAX_FRAMES = 14

    /** The summary in plain text, or null when [bytes] is not a tombstone. */
    fun of(bytes: ByteArray): String? = runCatching { read(bytes) }.getOrNull()

    private fun read(bytes: ByteArray): String? {
        var signal: Signal? = null
        var crashingTid = -1L
        var pid = -1L
        val causes = mutableListOf<String>()
        val command = mutableListOf<String>()
        var abort: String? = null
        val threads = mutableMapOf<Long, Thread>()
        var fingerprint: String? = null
        var timestamp: String? = null
        for (field in Fields(bytes)) {
            when (field.number) {
                2 -> fingerprint = field.text()
                4 -> timestamp = field.text()
                5 -> pid = field.number()
                6 -> crashingTid = field.number()
                9 -> field.text()?.let { command += it }
                10 -> signal = readSignal(field.bytes ?: continue)
                14 -> abort = field.text()
                15 -> readCause(field.bytes ?: continue)?.let { causes += it }
                16 -> readThreadEntry(field.bytes ?: continue)?.let { threads[it.id] = it }
            }
        }
        if (signal == null && threads.isEmpty()) return null
        return buildString {
            appendLine("Native crash of ${command.firstOrNull() ?: "the game"}${timestamp?.let { " at $it" }.orEmpty()}")
            signal?.let { appendLine("signal ${it.number} (${it.name}), code ${it.code} (${it.codeName})${it.fault?.let { address -> ", fault address 0x${address.toString(16)}" }.orEmpty()}") }
            causes.forEach { appendLine("cause: $it") }
            abort?.takeIf { it.isNotBlank() }?.let { appendLine("abort message: $it") }
            val crashed = threads[crashingTid]
            if (crashed != null) {
                appendLine()
                appendLine("crashing thread: ${crashed.name} (tid $crashingTid)")
                crashed.frames.take(MAX_FRAMES).forEachIndexed { index, frame -> appendLine("  #${index.toString().padStart(2, '0')} ${frame.describe()}") }
                if (crashed.frames.size > MAX_FRAMES) appendLine("  … ${crashed.frames.size - MAX_FRAMES} more")
            }
            val others = threads.values.filter { it.id != crashingTid }.map { it.name }.filter { it.isNotBlank() }
            if (others.isNotEmpty()) {
                appendLine()
                appendLine("other threads (${others.size}): ${others.take(30).joinToString(", ")}${if (others.size > 30) ", …" else ""}")
            }
            fingerprint?.let { appendLine("\nsystem: $it") }
        }.trim()
    }

    private data class Signal(val number: Long, val name: String, val code: Long, val codeName: String, val fault: Long?)

    private data class Frame(val relativePc: Long, val function: String?, val offset: Long, val file: String?) {
        fun describe(): String {
            val library = file?.substringAfterLast('/').orEmpty().ifEmpty { "?" }
            val where = if (function.isNullOrEmpty()) "$library+0x${relativePc.toString(16)}" else "$library ($function+$offset)"
            return where
        }
    }

    private class Thread(val id: Long, val name: String, val frames: List<Frame>)

    private fun readSignal(bytes: ByteArray): Signal {
        var number = 0L
        var name = ""
        var code = 0L
        var codeName = ""
        var fault: Long? = null
        for (field in Fields(bytes)) when (field.number) {
            1 -> number = field.number()
            2 -> name = field.text().orEmpty()
            3 -> code = field.number()
            4 -> codeName = field.text().orEmpty()
            9 -> fault = field.number()
        }
        return Signal(number, name, code, codeName, fault)
    }

    private fun readCause(bytes: ByteArray): String? = Fields(bytes).firstOrNull { it.number == 1 }?.text()

    /** One entry of the map of threads: the id, then the thread (its name, its registers, and its call stack). */
    private fun readThreadEntry(bytes: ByteArray): Thread? {
        var thread: ByteArray? = null
        for (field in Fields(bytes)) if (field.number == 2) thread = field.bytes
        val body = thread ?: return null
        var id = 0L
        var name = ""
        val frames = mutableListOf<Frame>()
        for (field in Fields(body)) when (field.number) {
            1 -> id = field.number()
            2 -> name = field.text().orEmpty()
            4 -> field.bytes?.let { frames += readFrame(it) }
        }
        return Thread(id, name, frames)
    }

    private fun readFrame(bytes: ByteArray): Frame {
        var relative = 0L
        var function: String? = null
        var offset = 0L
        var file: String? = null
        for (field in Fields(bytes)) when (field.number) {
            1 -> relative = field.number()
            4 -> function = field.text()
            5 -> offset = field.number()
            6 -> file = field.text()
        }
        return Frame(relative, function, offset, file)
    }

    /** One field of a protobuf message: its number, and its value as a number or as bytes. */
    private class Field(val number: Int, private val value: Long, val bytes: ByteArray?) {
        fun number(): Long = value
        fun text(): String? = bytes?.toString(Charsets.UTF_8)
    }

    /** The fields of one message, in order; reading stops at the first byte that is not a field. */
    private class Fields(private val data: ByteArray) : Iterable<Field> {
        override fun iterator(): Iterator<Field> = object : Iterator<Field> {
            private var at = 0
            private var next: Field? = null
            private fun advance(): Field? {
                if (at >= data.size) return null
                val key = varint() ?: return null
                val number = (key ushr 3).toInt()
                return when ((key and 7).toInt()) {
                    0 -> varint()?.let { Field(number, it, null) }
                    1 -> take(8)?.let { Field(number, it.foldIndexed(0L) { index, sum, byte -> sum or ((byte.toLong() and 0xff) shl (8 * index)) }, null) }
                    2 -> varint()?.let { length -> take(length.toInt())?.let { Field(number, 0, it) } }
                    5 -> take(4)?.let { Field(number, it.foldIndexed(0L) { index, sum, byte -> sum or ((byte.toLong() and 0xff) shl (8 * index)) }, null) }
                    else -> null
                }
            }

            private fun varint(): Long? {
                var result = 0L
                var shift = 0
                while (at < data.size && shift < 64) {
                    val byte = data[at++].toInt()
                    result = result or ((byte and 0x7f).toLong() shl shift)
                    if (byte and 0x80 == 0) return result
                    shift += 7
                }
                return null
            }

            private fun take(length: Int): ByteArray? {
                if (length < 0 || at + length > data.size) return null
                return data.copyOfRange(at, at + length).also { at += length }
            }

            override fun hasNext(): Boolean {
                if (next == null) next = advance()
                return next != null
            }

            override fun next(): Field = (next ?: advance() ?: throw NoSuchElementException()).also { next = null }
        }
    }
}
