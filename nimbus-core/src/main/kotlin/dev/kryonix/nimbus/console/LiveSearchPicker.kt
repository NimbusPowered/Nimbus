package dev.kryonix.nimbus.console

import dev.kryonix.nimbus.console.ConsoleFormatter.BOLD
import dev.kryonix.nimbus.console.ConsoleFormatter.CYAN
import dev.kryonix.nimbus.console.ConsoleFormatter.DIM
import dev.kryonix.nimbus.console.ConsoleFormatter.GREEN
import dev.kryonix.nimbus.console.ConsoleFormatter.RESET
import dev.kryonix.nimbus.console.ConsoleFormatter.YELLOW
import kotlinx.coroutines.*
import org.jline.terminal.Terminal

/**
 * Interactive live search picker for the terminal.
 *
 * Displays a text input field where the user types a query. Results are
 * fetched via a debounced callback and rendered below the input. The user
 * navigates with arrow keys and selects with ENTER.
 */
object LiveSearchPicker {

    /** One line of rendered search result (name line). */
    data class SearchLine(
        val sourceTag: String,
        val name: String,
        val author: String,
        val downloads: String,
        val description: String
    )

    private const val MAX_RESULTS = 5
    private const val DEBOUNCE_MS = 300L
    private const val POLL_MS = 50L

    private val SPINNER = charArrayOf('⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏')

    /**
     * Opens an interactive live search UI.
     *
     * @param terminal JLine terminal for raw I/O
     * @param title Header text (e.g. "Search plugins (1.21.4)")
     * @param initialQuery Pre-filled query string
     * @param search Callback that performs the actual search (debounced)
     * @param render Converts a result item to display strings
     * @return The selected item, or null if cancelled (ESC)
     */
    suspend fun <T> liveSearch(
        terminal: Terminal,
        title: String,
        initialQuery: String = "",
        search: suspend (query: String) -> List<T>,
        render: (T) -> SearchLine
    ): T? = coroutineScope {
        val query = StringBuilder(initialQuery)
        var cursor = 0
        var results: List<T> = emptyList()
        var loading = false
        var spinnerIdx = 0
        var searchJob: Job? = null
        var renderedLines = 0

        val w = terminal.writer()
        val originalAttrs = terminal.enterRawMode()
        val reader = terminal.reader()

        try {
            w.print("\u001B[?25l") // hide cursor
            w.flush()

            fun redraw() {
                if (renderedLines > 0) clearLines(w, renderedLines)
                renderedLines = draw(w, title, query.toString(), results, cursor, loading, spinnerIdx, render)
            }

            // Initial draw
            redraw()

            // If initial query provided, trigger search immediately
            if (query.isNotEmpty()) {
                loading = true
                redraw()
                searchJob = launch {
                    try {
                        val r = search(query.toString())
                        results = r
                        cursor = 0
                    } catch (_: Exception) {}
                    loading = false
                    redraw()
                }
            }

            while (isActive) {
                // Non-blocking read with timeout for coroutine cooperation
                val c = withContext(Dispatchers.IO) {
                    reader.read(POLL_MS.toLong())
                }

                if (c == -2) {
                    // Timeout — update spinner if loading
                    if (loading) {
                        spinnerIdx = (spinnerIdx + 1) % SPINNER.size
                        redraw()
                    }
                    continue
                }

                if (c == -1) break // EOF

                when (c) {
                    // ENTER — select current result
                    13, 10 -> {
                        if (results.isNotEmpty() && cursor in results.indices) {
                            clearLines(w, renderedLines)
                            restore(w, originalAttrs, terminal)
                            return@coroutineScope results[cursor]
                        }
                    }

                    // Ctrl+C or ESC
                    3 -> {
                        clearLines(w, renderedLines)
                        restore(w, originalAttrs, terminal)
                        return@coroutineScope null
                    }

                    27 -> {
                        // ESC sequence — check for arrow keys
                        val next = withContext(Dispatchers.IO) { reader.peek(50) }
                        if (next == -2 || next == -1) {
                            // Plain ESC
                            clearLines(w, renderedLines)
                            restore(w, originalAttrs, terminal)
                            return@coroutineScope null
                        }
                        withContext(Dispatchers.IO) { reader.read() } // consume '['
                        val arrow = withContext(Dispatchers.IO) { reader.read() }
                        when (arrow) {
                            65 -> { // UP
                                if (results.isNotEmpty()) {
                                    cursor = (cursor - 1 + results.size) % results.size
                                    redraw()
                                }
                            }
                            66 -> { // DOWN
                                if (results.isNotEmpty()) {
                                    cursor = (cursor + 1) % results.size
                                    redraw()
                                }
                            }
                        }
                    }

                    // Backspace
                    127, 8 -> {
                        if (query.isNotEmpty()) {
                            query.deleteCharAt(query.length - 1)
                            triggerSearch(query.toString(), search) { r ->
                                results = r; cursor = 0; loading = false; redraw()
                            }.let { job ->
                                searchJob?.cancel()
                                searchJob = launch {
                                    loading = true; redraw()
                                    delay(DEBOUNCE_MS)
                                    job()
                                }
                            }
                            redraw()
                        }
                    }

                    // Printable characters
                    in 32..126 -> {
                        query.append(c.toChar())
                        searchJob?.cancel()
                        searchJob = launch {
                            loading = true; redraw()
                            delay(DEBOUNCE_MS)
                            try {
                                val r = search(query.toString())
                                results = r
                                cursor = 0
                            } catch (_: Exception) {}
                            loading = false
                            redraw()
                        }
                        redraw()
                    }
                }
            }

            clearLines(w, renderedLines)
            restore(w, originalAttrs, terminal)
            null
        } catch (e: Exception) {
            if (renderedLines > 0) clearLines(w, renderedLines)
            restore(w, originalAttrs, terminal)
            throw e
        }
    }

    // ── Rendering ──────────────────────────────────────────

    private fun <T> draw(
        w: java.io.Writer,
        title: String,
        query: String,
        results: List<T>,
        cursor: Int,
        loading: Boolean,
        spinnerIdx: Int,
        render: (T) -> SearchLine
    ): Int {
        var lines = 0

        // Title
        w.write("  ${BOLD}$title${RESET}\n")
        lines++

        // Search input
        val spinner = if (loading) " ${YELLOW}${SPINNER[spinnerIdx]}${RESET}" else ""
        w.write("  ${DIM}>${RESET} ${query}${DIM}_${RESET}$spinner\n")
        lines++

        // Blank line
        w.write("\n")
        lines++

        if (results.isEmpty()) {
            if (query.isNotEmpty() && !loading) {
                w.write("  ${DIM}No plugins found.$RESET\n")
                lines++
            } else if (query.isEmpty()) {
                w.write("  ${DIM}Type to search...$RESET\n")
                lines++
            }
        } else {
            val visible = results.take(MAX_RESULTS)
            for ((i, item) in visible.withIndex()) {
                val line = render(item)
                val isCursor = i == cursor
                val pointer = if (isCursor) "${CYAN}>${RESET} " else "  "
                val nameColor = if (isCursor) CYAN else ""
                val nameReset = if (isCursor) RESET else ""

                // Line 1: source tag, name, author, downloads
                val tag = "${DIM}${line.sourceTag}${RESET}"
                val dl = "${DIM}${line.downloads}${RESET}"
                val author = "${DIM}${line.author}${RESET}"
                w.write("  $pointer$tag  $nameColor${line.name}$nameReset  $author  $dl\n")
                lines++

                // Line 2: description (indented)
                val descIndent = if (isCursor) "       " else "     "
                w.write("  $descIndent${DIM}${line.description}${RESET}\n")
                lines++
            }

            if (results.size > MAX_RESULTS) {
                w.write("  ${DIM}  ... and ${results.size - MAX_RESULTS} more${RESET}\n")
                lines++
            }
        }

        // Blank + hint line
        w.write("\n")
        lines++
        w.write("  ${ConsoleFormatter.hint("↑↓ navigate  ·  enter select  ·  esc cancel")}\n")
        lines++

        w.flush()
        return lines
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun <T> triggerSearch(
        query: String,
        search: suspend (String) -> List<T>,
        onResult: (List<T>) -> Unit
    ): suspend () -> Unit = {
        try {
            val r = search(query)
            onResult(r)
        } catch (_: Exception) {
            onResult(emptyList())
        }
    }

    private fun clearLines(w: java.io.Writer, count: Int) {
        for (i in 0 until count) w.write("\u001B[A")
        for (i in 0 until count) {
            w.write("\u001B[2K")
            if (i < count - 1) w.write("\u001B[B")
        }
        for (i in 0 until count - 1) w.write("\u001B[A")
        w.write("\r")
        w.flush()
    }

    private fun restore(w: java.io.Writer, attrs: org.jline.terminal.Attributes, terminal: Terminal) {
        terminal.setAttributes(attrs)
        w.write("\u001B[?25h") // show cursor
        (w as? java.io.Flushable)?.flush()
    }
}
