package dev.lumenchess.engine.api

class UciProtocolException(message: String) : IllegalArgumentException(message)

enum class UciScoreBound { EXACT, LOWER, UPPER }

sealed interface UciScore {
    val bound: UciScoreBound

    data class Centipawns(val value: Int, override val bound: UciScoreBound = UciScoreBound.EXACT) : UciScore
    data class Mate(val moves: Int, override val bound: UciScoreBound = UciScoreBound.EXACT) : UciScore
}

data class UciInfo(
    val depth: Int? = null,
    val selectiveDepth: Int? = null,
    val multiPv: Int? = null,
    val score: UciScore? = null,
    val nodes: Long? = null,
    val nodesPerSecond: Long? = null,
    val timeMillis: Long? = null,
    val hashFullPermille: Int? = null,
    val tablebaseHits: Long? = null,
    val principalVariation: List<String> = emptyList(),
    val string: String? = null,
    val unparsedTokens: List<String> = emptyList(),
)

sealed interface UciOption {
    val name: String

    data class Spin(
        override val name: String,
        val defaultValue: Int,
        val min: Int,
        val max: Int,
    ) : UciOption

    data class Check(override val name: String, val defaultValue: Boolean) : UciOption

    data class Combo(
        override val name: String,
        val defaultValue: String,
        val values: List<String>,
    ) : UciOption

    data class Button(override val name: String) : UciOption

    data class StringOption(override val name: String, val defaultValue: String) : UciOption
}

sealed interface UciEvent {
    data class IdName(val name: String) : UciEvent
    data class IdAuthor(val author: String) : UciEvent
    data class Option(val option: UciOption) : UciEvent
    data class Info(val info: UciInfo) : UciEvent
    data class BestMove(val bestMove: String?, val ponder: String?) : UciEvent
    data object UciOk : UciEvent
    data object ReadyOk : UciEvent
    data class Unknown(val line: String) : UciEvent
}

object UciProtocolParser {
    fun parse(rawLine: String): UciEvent {
        val line = rawLine.trim()
        if (line.isEmpty()) return UciEvent.Unknown(rawLine)
        return when {
            line == "uciok" -> UciEvent.UciOk
            line == "readyok" -> UciEvent.ReadyOk
            line == "id" -> fail("Malformed UCI id line: $rawLine")
            line.startsWith("id ") -> parseId(line)
            line == "option" || line.startsWith("option ") -> parseOption(line)
            line == "info" || line.startsWith("info ") -> parseInfo(line)
            line == "bestmove" || line.startsWith("bestmove ") -> parseBestMove(line)
            else -> UciEvent.Unknown(line)
        }
    }

    private fun parseId(line: String): UciEvent {
        return when {
            line.startsWith("id name ") -> {
                val value = line.removePrefix("id name ").trim()
                if (value.isEmpty()) fail("Missing engine name in: $line")
                UciEvent.IdName(value)
            }
            line.startsWith("id author ") -> {
                val value = line.removePrefix("id author ").trim()
                if (value.isEmpty()) fail("Missing engine author in: $line")
                UciEvent.IdAuthor(value)
            }
            else -> fail("Malformed UCI id line: $line")
        }
    }

    private fun parseOption(line: String): UciEvent {
        val tokens = line.split(Regex("\\s+"))
        if (tokens.size < 5 || tokens[0] != "option" || tokens[1] != "name") {
            fail("Malformed UCI option line: $line")
        }
        val typeIndex = tokens.indexOf("type")
        if (typeIndex < 3 || typeIndex == tokens.lastIndex) fail("Missing option type in: $line")
        val name = tokens.subList(2, typeIndex).joinToString(" ").trim()
        if (name.isEmpty()) fail("Missing option name in: $line")
        val type = tokens[typeIndex + 1]
        val rest = tokens.drop(typeIndex + 2)

        val option = when (type) {
            "spin" -> {
                val default = markerInt(rest, "default", line)
                val min = markerInt(rest, "min", line)
                val max = markerInt(rest, "max", line)
                if (min > max || default !in min..max) fail("Invalid spin bounds in: $line")
                UciOption.Spin(name, default, min, max)
            }
            "check" -> {
                val value = markerSingle(rest, "default", line)
                val parsed = when (value) {
                    "true" -> true
                    "false" -> false
                    else -> fail("Invalid check default in: $line")
                }
                UciOption.Check(name, parsed)
            }
            "combo" -> parseCombo(name, rest, line)
            "button" -> {
                if (rest.isNotEmpty()) fail("Unexpected button option fields in: $line")
                UciOption.Button(name)
            }
            "string" -> {
                val defaultIndex = rest.indexOf("default")
                if (defaultIndex < 0) fail("Missing string default in: $line")
                val value = rest.drop(defaultIndex + 1).joinToString(" ")
                UciOption.StringOption(name, value)
            }
            else -> fail("Unsupported UCI option type '$type' in: $line")
        }
        return UciEvent.Option(option)
    }

    private fun parseCombo(name: String, rest: List<String>, line: String): UciOption.Combo {
        val defaultIndex = rest.indexOf("default")
        if (defaultIndex < 0) fail("Missing combo default in: $line")
        val firstVar = rest.indexOf("var")
        val defaultEnd = if (firstVar >= 0) firstVar else rest.size
        if (defaultIndex + 1 >= defaultEnd) fail("Missing combo default value in: $line")
        val defaultValue = rest.subList(defaultIndex + 1, defaultEnd).joinToString(" ")

        val values = mutableListOf<String>()
        var index = firstVar
        while (index >= 0 && index < rest.size) {
            val nextVar = rest.indexOf("var", startIndex = index + 1).let { if (it < 0) rest.size else it }
            if (index + 1 >= nextVar) fail("Missing combo value in: $line")
            values += rest.subList(index + 1, nextVar).joinToString(" ")
            index = if (nextVar == rest.size) -1 else nextVar
        }
        if (values.isEmpty()) fail("Combo option has no values in: $line")
        return UciOption.Combo(name, defaultValue, values)
    }

    private fun parseInfo(line: String): UciEvent {
        val tokens = line.split(Regex("\\s+")).drop(1)
        var depth: Int? = null
        var selectiveDepth: Int? = null
        var multiPv: Int? = null
        var score: UciScore? = null
        var nodes: Long? = null
        var nps: Long? = null
        var time: Long? = null
        var hashFull: Int? = null
        var tablebaseHits: Long? = null
        var pv: List<String> = emptyList()
        var string: String? = null
        val unparsed = mutableListOf<String>()

        var i = 0
        while (i < tokens.size) {
            when (val key = tokens[i]) {
                "depth" -> { depth = intAfter(tokens, i, key, line); i += 2 }
                "seldepth" -> { selectiveDepth = intAfter(tokens, i, key, line); i += 2 }
                "multipv" -> { multiPv = intAfter(tokens, i, key, line).also { if (it <= 0) fail("Invalid multipv in: $line") }; i += 2 }
                "nodes" -> { nodes = longAfter(tokens, i, key, line); i += 2 }
                "nps" -> { nps = longAfter(tokens, i, key, line); i += 2 }
                "time" -> { time = longAfter(tokens, i, key, line); i += 2 }
                "hashfull" -> { hashFull = intAfter(tokens, i, key, line); i += 2 }
                "tbhits" -> { tablebaseHits = longAfter(tokens, i, key, line); i += 2 }
                "score" -> {
                    if (i + 2 >= tokens.size) fail("Malformed score in: $line")
                    val kind = tokens[i + 1]
                    val value = tokens[i + 2].toIntOrNull() ?: fail("Invalid score value in: $line")
                    var bound = UciScoreBound.EXACT
                    var consumed = 3
                    if (i + 3 < tokens.size) {
                        when (tokens[i + 3]) {
                            "lowerbound" -> { bound = UciScoreBound.LOWER; consumed = 4 }
                            "upperbound" -> { bound = UciScoreBound.UPPER; consumed = 4 }
                        }
                    }
                    score = when (kind) {
                        "cp" -> UciScore.Centipawns(value, bound)
                        "mate" -> UciScore.Mate(value, bound)
                        else -> fail("Unknown score type '$kind' in: $line")
                    }
                    i += consumed
                }
                "pv" -> {
                    pv = tokens.drop(i + 1)
                    i = tokens.size
                }
                "string" -> {
                    string = tokens.drop(i + 1).joinToString(" ")
                    i = tokens.size
                }
                else -> {
                    unparsed += key
                    i += 1
                }
            }
        }

        return UciEvent.Info(
            UciInfo(
                depth = depth,
                selectiveDepth = selectiveDepth,
                multiPv = multiPv,
                score = score,
                nodes = nodes,
                nodesPerSecond = nps,
                timeMillis = time,
                hashFullPermille = hashFull,
                tablebaseHits = tablebaseHits,
                principalVariation = pv,
                string = string,
                unparsedTokens = unparsed,
            ),
        )
    }

    private fun parseBestMove(line: String): UciEvent {
        val tokens = line.split(Regex("\\s+"))
        if (tokens.size < 2) fail("Missing bestmove value in: $line")
        val bestMove = nullMove(tokens[1])
        if (tokens.size == 2) return UciEvent.BestMove(bestMove, null)
        if (tokens.size != 4 || tokens[2] != "ponder") fail("Malformed bestmove line: $line")
        return UciEvent.BestMove(bestMove, nullMove(tokens[3]))
    }

    private fun nullMove(token: String): String? = when (token) {
        "0000", "(none)" -> null
        else -> token
    }

    private fun markerInt(tokens: List<String>, marker: String, line: String): Int =
        markerSingle(tokens, marker, line).toIntOrNull() ?: fail("Invalid $marker value in: $line")

    private fun markerSingle(tokens: List<String>, marker: String, line: String): String {
        val index = tokens.indexOf(marker)
        if (index < 0 || index == tokens.lastIndex) fail("Missing $marker value in: $line")
        return tokens[index + 1]
    }

    private fun intAfter(tokens: List<String>, index: Int, field: String, line: String): Int =
        tokens.getOrNull(index + 1)?.toIntOrNull() ?: fail("Invalid $field in: $line")

    private fun longAfter(tokens: List<String>, index: Int, field: String, line: String): Long =
        tokens.getOrNull(index + 1)?.toLongOrNull() ?: fail("Invalid $field in: $line")

    private fun fail(message: String): Nothing = throw UciProtocolException(message)
}

sealed interface UciCommand {
    data object Initialize : UciCommand
    data object IsReady : UciCommand
    data object NewGame : UciCommand
    data class SetOption(val name: String, val value: String? = null) : UciCommand {
        init { require(name.isNotBlank()) { "UCI option name cannot be blank" } }
    }
    data class Position(val fen: String, val moves: List<String> = emptyList()) : UciCommand {
        init { require(fen.isNotBlank()) { "UCI position FEN cannot be blank" } }
    }
    data class Go(
        val depth: Int? = null,
        val nodes: Long? = null,
        val moveTimeMillis: Long? = null,
    ) : UciCommand {
        init {
            require(depth == null || depth > 0) { "UCI go depth must be positive" }
            require(nodes == null || nodes > 0L) { "UCI go nodes must be positive" }
            require(moveTimeMillis == null || moveTimeMillis > 0L) { "UCI go movetime must be positive" }
        }
    }
    data object Stop : UciCommand
    data object Quit : UciCommand
}

object UciCommandEncoder {
    fun encode(command: UciCommand): String = when (command) {
        UciCommand.Initialize -> "uci"
        UciCommand.IsReady -> "isready"
        UciCommand.NewGame -> "ucinewgame"
        UciCommand.Stop -> "stop"
        UciCommand.Quit -> "quit"
        is UciCommand.SetOption -> buildString {
            append("setoption name ").append(command.name)
            command.value?.let { append(" value ").append(it) }
        }
        is UciCommand.Position -> buildString {
            append("position fen ").append(command.fen)
            if (command.moves.isNotEmpty()) append(" moves ").append(command.moves.joinToString(" "))
        }
        is UciCommand.Go -> buildString {
            append("go")
            command.depth?.let { append(" depth ").append(it) }
            command.nodes?.let { append(" nodes ").append(it) }
            command.moveTimeMillis?.let { append(" movetime ").append(it) }
        }
    }
}
