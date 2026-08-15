package dev.lumenchess.core.chess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PgnTokenizerTest {
    @Test
    fun tokenizesTagsEscapedStringsMovetextCommentsNagsAndRavs() {
        val input = """
            [Event "A \\"quoted\\" \\\\ path"]
            [Result "1-0"]

            1. e4 {brace comment} e5 $1 (1... c5?! ;line comment
            2. Nf3) 2. Qh5 1-0
        """.trimIndent()

        val tokens = PgnTokenizer.tokenize(input)
        assertEquals(PgnTokenType.LBRACKET, tokens.first().type)
        assertTrue(tokens.any { it.type == PgnTokenType.STRING && it.text == "A \"quoted\" \\ path" })
        assertTrue(tokens.any { it.type == PgnTokenType.COMMENT && it.text == "brace comment" })
        assertTrue(tokens.any { it.type == PgnTokenType.COMMENT && it.text == "line comment" })
        assertTrue(tokens.any { it.type == PgnTokenType.NAG && it.text == "1" })
        assertTrue(tokens.any { it.type == PgnTokenType.LPAREN })
        assertTrue(tokens.any { it.type == PgnTokenType.RPAREN })
        assertTrue(tokens.any { it.type == PgnTokenType.SYMBOL && it.text == "c5?!" })
        assertTrue(tokens.any { it.type == PgnTokenType.RESULT && it.text == "1-0" })
        assertTrue(tokens.all { it.index >= 0 })
    }

    @Test
    fun recognizesMoveNumbersAndBlackEllipsisAsSeparateTokens() {
        val tokens = PgnTokenizer.tokenize("12... Nf6 13. e4 *")
        assertEquals(
            listOf(
                PgnTokenType.INTEGER,
                PgnTokenType.PERIOD,
                PgnTokenType.PERIOD,
                PgnTokenType.PERIOD,
                PgnTokenType.SYMBOL,
                PgnTokenType.INTEGER,
                PgnTokenType.PERIOD,
                PgnTokenType.SYMBOL,
                PgnTokenType.RESULT,
            ),
            tokens.map { it.type },
        )
    }

    @Test
    fun ignoresPgnEscapeLinesBeginningWithPercent() {
        val tokens = PgnTokenizer.tokenize("% generated metadata\n1. e4 *")
        assertEquals("1", tokens.first().text)
        assertTrue(tokens.none { it.text.contains("generated") })
    }

    @Test
    fun unterminatedBraceCommentFailsWithSourceIndex() {
        val error = assertThrows(PgnParseException::class.java) {
            PgnTokenizer.tokenize("1. e4 {never closed")
        }
        assertEquals(6, error.index)
        assertTrue(error.message.orEmpty().contains("comment", ignoreCase = true))
    }

    @Test
    fun unterminatedTagStringFailsWithSourceIndex() {
        val error = assertThrows(PgnParseException::class.java) {
            PgnTokenizer.tokenize("[Event \"oops]")
        }
        assertEquals(7, error.index)
        assertTrue(error.message.orEmpty().contains("string", ignoreCase = true))
    }

    @Test
    fun malformedNagFailsClearly() {
        val missingDigits = assertThrows(PgnParseException::class.java) {
            PgnTokenizer.tokenize("1. e4 $ Nf6 *")
        }
        assertTrue(missingDigits.message.orEmpty().contains("NAG"))

        val outOfRange = assertThrows(PgnParseException::class.java) {
            PgnTokenizer.tokenize("1. e4 $256 *")
        }
        assertTrue(outOfRange.message.orEmpty().contains("0..255"))
    }

    @Test
    fun unexpectedClosingBraceIsNotSilentlyAccepted() {
        val error = assertThrows(PgnParseException::class.java) {
            PgnTokenizer.tokenize("1. e4 } *")
        }
        assertEquals(6, error.index)
    }
}
