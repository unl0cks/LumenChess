package dev.lumenchess.core.chess

class PgnParseException(
    message: String,
    val index: Int,
    val token: String? = null,
    val ply: Int? = null,
    cause: Throwable? = null,
) : IllegalArgumentException(
    buildString {
        append(message)
        append(" at index ")
        append(index)
        token?.let { append(" near '").append(it).append('\'') }
        ply?.let { append(" (ply ").append(it).append(')') }
    },
    cause,
)

enum class PgnTokenType {
    LBRACKET,
    RBRACKET,
    LPAREN,
    RPAREN,
    PERIOD,
    STRING,
    INTEGER,
    NAG,
    COMMENT,
    SYMBOL,
    RESULT,
}

data class PgnToken(
    val type: PgnTokenType,
    val text: String,
    val index: Int,
)

/** Syntax-only PGN scanner. Chess semantics and SAN legality are handled by [Pgn]. */
object PgnTokenizer {
    fun tokenize(input: String): List<PgnToken> {
        val tokens = ArrayList<PgnToken>()
        var i = 0
        var lineStart = true

        while (i < input.length) {
            val c = input[i]
            when {
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    i++
                    lineStart = true
                }

                c.isWhitespace() -> i++

                c == '%' && lineStart -> {
                    while (i < input.length && input[i] != '\r' && input[i] != '\n') i++
                }

                else -> {
                    lineStart = false
                    when (c) {
                        '[' -> tokens += PgnToken(PgnTokenType.LBRACKET, "[", i++)
                        ']' -> tokens += PgnToken(PgnTokenType.RBRACKET, "]", i++)
                        '(' -> tokens += PgnToken(PgnTokenType.LPAREN, "(", i++)
                        ')' -> tokens += PgnToken(PgnTokenType.RPAREN, ")", i++)
                        '.' -> tokens += PgnToken(PgnTokenType.PERIOD, ".", i++)
                        '}' -> throw PgnParseException("Unexpected closing comment brace", i, "}")
                        '{' -> {
                            val start = i
                            i++
                            val contentStart = i
                            while (i < input.length && input[i] != '}') i++
                            if (i >= input.length) {
                                throw PgnParseException("Unterminated brace comment", start)
                            }
                            tokens += PgnToken(
                                PgnTokenType.COMMENT,
                                input.substring(contentStart, i).trim(),
                                start,
                            )
                            i++
                        }

                        ';' -> {
                            val start = i
                            i++
                            val contentStart = i
                            while (i < input.length && input[i] != '\r' && input[i] != '\n') i++
                            tokens += PgnToken(
                                PgnTokenType.COMMENT,
                                input.substring(contentStart, i).trim(),
                                start,
                            )
                        }

                        '"' -> {
                            val start = i
                            i++
                            val value = StringBuilder()
                            var closed = false
                            while (i < input.length) {
                                when (val ch = input[i]) {
                                    '"' -> {
                                        i++
                                        closed = true
                                        break
                                    }

                                    '\r', '\n' -> throw PgnParseException("Unterminated tag string", start)
                                    '\\' -> {
                                        if (i + 1 >= input.length) {
                                            throw PgnParseException("Unterminated tag string escape", start)
                                        }
                                        val escaped = input[i + 1]
                                        if (escaped == '"' || escaped == '\\') {
                                            value.append(escaped)
                                            i += 2
                                        } else {
                                            // The PGN specification only gives special meaning to escaped quote/backslash.
                                            // Preserve unknown escapes verbatim rather than silently discarding a character.
                                            value.append('\\').append(escaped)
                                            i += 2
                                        }
                                    }

                                    else -> {
                                        value.append(ch)
                                        i++
                                    }
                                }
                            }
                            if (!closed) throw PgnParseException("Unterminated tag string", start)
                            tokens += PgnToken(PgnTokenType.STRING, value.toString(), start)
                        }

                        '$' -> {
                            val start = i
                            i++
                            val digitsStart = i
                            while (i < input.length && input[i].isDigit()) i++
                            if (digitsStart == i) throw PgnParseException("NAG must contain digits", start, "$")
                            val text = input.substring(digitsStart, i)
                            val value = text.toIntOrNull()
                                ?: throw PgnParseException("Invalid NAG", start, text)
                            if (value !in 0..255) {
                                throw PgnParseException("NAG must be in 0..255", start, text)
                            }
                            tokens += PgnToken(PgnTokenType.NAG, text, start)
                        }

                        '*' -> {
                            tokens += PgnToken(PgnTokenType.RESULT, "*", i)
                            i++
                        }

                        else -> {
                            val result = resultAt(input, i)
                            if (result != null) {
                                tokens += PgnToken(PgnTokenType.RESULT, result, i)
                                i += result.length
                            } else if (c.isDigit()) {
                                val start = i
                                while (i < input.length && input[i].isDigit()) i++
                                tokens += PgnToken(PgnTokenType.INTEGER, input.substring(start, i), start)
                            } else {
                                val start = i
                                while (i < input.length && !isSymbolDelimiter(input[i])) i++
                                if (start == i) {
                                    throw PgnParseException("Unexpected PGN character", i, input[i].toString())
                                }
                                tokens += PgnToken(PgnTokenType.SYMBOL, input.substring(start, i), start)
                            }
                        }
                    }
                }
            }
        }

        return tokens
    }

    private fun resultAt(input: String, index: Int): String? {
        for (candidate in listOf("1/2-1/2", "1-0", "0-1")) {
            if (input.regionMatches(index, candidate, 0, candidate.length) &&
                (index + candidate.length == input.length || isSymbolDelimiter(input[index + candidate.length]))
            ) {
                return candidate
            }
        }
        return null
    }

    private fun isSymbolDelimiter(c: Char): Boolean =
        c.isWhitespace() || c in charArrayOf('[', ']', '(', ')', '{', '}', ';', '"', '$', '.')
}
