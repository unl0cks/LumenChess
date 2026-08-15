package dev.lumenchess.core.chess

/**
 * Strict PGN import/export built on [PgnTokenizer], [San], and the immutable [GameTree].
 *
 * The parser never implements chess legality. SAN is resolved through [San.parse], which in turn
 * resolves candidates exclusively against [MoveGenerator.legalMoves]. Any malformed or illegal
 * input aborts the whole parse instead of returning a partially corrupted game.
 */
object Pgn {
    fun parseGame(text: String): GameTree {
        val games = parseGames(text)
        if (games.size != 1) {
            throw PgnParseException(
                "Expected exactly one PGN game, found ${games.size}",
                index = 0,
            )
        }
        return games.single()
    }

    fun parseGames(text: String): List<GameTree> =
        Parser(PgnTokenizer.tokenize(text), text.length).parseAll()

    fun serialize(game: GameTree): String = Writer(game).write()

    private class Parser(
        private val tokens: List<PgnToken>,
        private val sourceLength: Int,
    ) {
        private var cursor = 0
        private lateinit var tree: GameTree

        fun parseAll(): List<GameTree> {
            val games = ArrayList<GameTree>()
            while (!atEnd()) {
                games += parseOne()
            }
            return games
        }

        private fun parseOne(): GameTree {
            val headerEntries = parseHeaders()
            val headers = LinkedHashMap<String, String>()
            for (entry in headerEntries) {
                if (headers.put(entry.name, entry.value) != null) {
                    throw PgnParseException("Duplicate PGN tag '${entry.name}'", entry.index, entry.name)
                }
            }

            val variant = parseVariant(headers["Variant"], headerEntries)
            val startPosition = parseStartPosition(headers, headerEntries, variant)
            tree = GameTree.create(startPosition = startPosition, headers = headers)

            val headerResultMarker = headers["Result"]?.let { marker ->
                parseResultMarker(marker, headerIndex("Result", headerEntries), "Result tag")
                marker
            }

            val movetextResult = parseSequence(
                startParentId = tree.rootId,
                topLevel = true,
            ) ?: throw PgnParseException(
                "PGN movetext is missing a terminating result",
                sourceLength,
            )

            if (headerResultMarker != null && headerResultMarker != movetextResult.marker) {
                throw PgnParseException(
                    "Result tag '$headerResultMarker' contradicts movetext result '${movetextResult.marker}'",
                    movetextResult.index,
                    movetextResult.marker,
                )
            }

            tree = tree.withResult(movetextResult.result)
            return tree
        }

        private data class HeaderEntry(val name: String, val value: String, val index: Int)

        private fun parseHeaders(): List<HeaderEntry> {
            val entries = ArrayList<HeaderEntry>()
            while (peekType() == PgnTokenType.LBRACKET) {
                val open = consume()
                val name = requireToken(PgnTokenType.SYMBOL, "Expected tag name after '['")
                val value = requireToken(PgnTokenType.STRING, "Expected quoted tag value for '${name.text}'")
                val close = peek()
                    ?: throw PgnParseException("Unterminated PGN tag '${name.text}'", sourceLength, name.text)
                if (close.type != PgnTokenType.RBRACKET) {
                    throw PgnParseException("Expected ']' to close PGN tag '${name.text}'", close.index, close.text)
                }
                consume()
                entries += HeaderEntry(name.text, value.text, open.index)
            }
            return entries
        }

        private fun parseVariant(value: String?, entries: List<HeaderEntry>): Variant {
            if (value == null) return Variant.STANDARD
            return when (value.trim().lowercase().replace("-", "").replace("_", "").replace(" ", "")) {
                "standard", "chess" -> Variant.STANDARD
                "chess960", "fischerrandom", "fischerandom" -> Variant.CHESS960
                else -> throw PgnParseException(
                    "Unsupported PGN Variant '$value'",
                    headerIndex("Variant", entries),
                    value,
                )
            }
        }

        private fun parseStartPosition(
            headers: Map<String, String>,
            entries: List<HeaderEntry>,
            variant: Variant,
        ): Position {
            val setUp = headers["SetUp"]
            if (setUp != null && setUp !in setOf("0", "1")) {
                throw PgnParseException(
                    "SetUp tag must be '0' or '1', got '$setUp'",
                    headerIndex("SetUp", entries),
                    setUp,
                )
            }
            val fen = headers["FEN"]
            if (setUp == "1" && fen == null) {
                throw PgnParseException(
                    "SetUp \"1\" requires a FEN tag",
                    headerIndex("SetUp", entries),
                    "SetUp",
                )
            }
            if (setUp == "0" && fen != null) {
                throw PgnParseException(
                    "SetUp \"0\" contradicts the supplied FEN tag",
                    headerIndex("SetUp", entries),
                    "SetUp",
                )
            }
            if (variant == Variant.CHESS960 && fen == null) {
                throw PgnParseException(
                    "Chess960 PGN requires an exact FEN starting position",
                    headerIndex("Variant", entries),
                    headers["Variant"],
                )
            }
            if (fen == null) return Position.initial()

            return try {
                Fen.parse(fen, variant)
            } catch (error: IllegalArgumentException) {
                throw PgnParseException(
                    "Invalid FEN tag: ${error.message ?: "invalid position"}",
                    headerIndex("FEN", entries),
                    fen,
                    cause = error,
                )
            }
        }

        private data class ParsedResult(val marker: String, val result: GameResult?, val index: Int)

        /**
         * Parses one move sequence. A RAV starts from the parent of the immediately preceding move,
         * i.e. the position before the move which the variation replaces.
         */
        private fun parseSequence(startParentId: GameNodeId, topLevel: Boolean): ParsedResult? {
            var currentId = startParentId
            var lastMoveId: GameNodeId? = null
            var pendingLeadingComments = emptyList<String>()
            var commentsForNextMove = false

            while (!atEnd()) {
                val token = peek()!!
                when (token.type) {
                    PgnTokenType.INTEGER -> {
                        consume()
                        if (peekType() != PgnTokenType.PERIOD) {
                            throw PgnParseException("Move number must be followed by '.'", token.index, token.text)
                        }
                        while (peekType() == PgnTokenType.PERIOD) consume()
                        commentsForNextMove = true
                    }

                    PgnTokenType.PERIOD ->
                        throw PgnParseException("Unexpected '.' without a move number", token.index, token.text)

                    PgnTokenType.COMMENT -> {
                        consume()
                        if (lastMoveId == null) {
                            if (topLevel && currentId == tree.rootId && !commentsForNextMove) {
                                tree = tree.withRootComments(tree.rootComments + token.text)
                            } else {
                                pendingLeadingComments = pendingLeadingComments + token.text
                            }
                        } else if (commentsForNextMove) {
                            pendingLeadingComments = pendingLeadingComments + token.text
                        } else {
                            val node = tree.node(lastMoveId)
                            tree = tree.withNodeMetadata(lastMoveId, comments = node.comments + token.text)
                        }
                    }

                    PgnTokenType.NAG -> {
                        consume()
                        val id = lastMoveId
                            ?: throw PgnParseException("NAG has no preceding move", token.index, "$${token.text}")
                        val node = tree.node(id)
                        tree = tree.withNodeMetadata(id, nags = node.nags + Nag(token.text.toInt()))
                    }

                    PgnTokenType.SYMBOL -> {
                        consume()
                        val detachedNag = symbolicNag(token.text)
                        if (detachedNag != null) {
                            val id = lastMoveId
                                ?: throw PgnParseException("Symbolic annotation has no preceding move", token.index, token.text)
                            val node = tree.node(id)
                            tree = tree.withNodeMetadata(id, nags = node.nags + detachedNag)
                            continue
                        }

                        val (sanText, attachedNag) = splitAttachedNag(token.text)
                        val position = tree.node(currentId).position
                        val move = try {
                            San.parse(position, sanText)
                        } catch (error: SanException) {
                            throw PgnParseException(
                                "Illegal or invalid SAN '${token.text}': ${error.message}",
                                token.index,
                                token.text,
                                plyFor(position),
                                error,
                            )
                        }

                        val added = try {
                            tree.addMove(
                                parentId = currentId,
                                move = move,
                                leadingComments = pendingLeadingComments,
                                nags = attachedNag?.let(::listOf).orEmpty(),
                            )
                        } catch (error: IllegalArgumentException) {
                            throw PgnParseException(
                                "Illegal move '${token.text}': ${error.message}",
                                token.index,
                                token.text,
                                plyFor(position),
                                error,
                            )
                        }
                        tree = added.tree
                        currentId = added.nodeId
                        lastMoveId = added.nodeId
                        pendingLeadingComments = emptyList()
                        commentsForNextMove = false
                    }

                    PgnTokenType.LPAREN -> {
                        consume()
                        if (pendingLeadingComments.isNotEmpty()) {
                            throw PgnParseException(
                                "Comment before variation is not attached to a move",
                                token.index,
                                token.text,
                            )
                        }
                        val replacedMoveId = lastMoveId
                            ?: throw PgnParseException("Variation has no preceding move to replace", token.index, token.text)
                        val branchBase = tree.node(replacedMoveId).parentId
                            ?: throw PgnParseException("Variation cannot branch before the game root", token.index, token.text)
                        val nestedResult = parseSequence(branchBase, topLevel = false)
                        check(nestedResult == null)
                        // The outer sequence continues exactly where it was; RAV parsing must not
                        // advance the mainline cursor.
                    }

                    PgnTokenType.RPAREN -> {
                        if (topLevel) {
                            throw PgnParseException("Unexpected ')' outside a variation", token.index, token.text)
                        }
                        consume()
                        if (pendingLeadingComments.isNotEmpty()) {
                            throw PgnParseException("Variation ends after an unattached comment", token.index, token.text)
                        }
                        return null
                    }

                    PgnTokenType.RESULT -> {
                        if (!topLevel) {
                            throw PgnParseException("Result marker is not allowed inside a variation", token.index, token.text)
                        }
                        if (pendingLeadingComments.isNotEmpty()) {
                            throw PgnParseException("Result follows an unattached comment", token.index, token.text)
                        }
                        consume()
                        return ParsedResult(token.text, parseResultMarker(token.text, token.index, "movetext"), token.index)
                    }

                    PgnTokenType.LBRACKET,
                    PgnTokenType.RBRACKET,
                    PgnTokenType.STRING ->
                        throw PgnParseException("Unexpected token in movetext", token.index, token.text)
                }
            }

            if (!topLevel) {
                throw PgnParseException("Unclosed PGN variation: missing ')'", sourceLength)
            }
            return null
        }

        private fun parseResultMarker(marker: String, index: Int, context: String): GameResult? = when (marker) {
            "1-0" -> GameResult.WHITE_WIN
            "0-1" -> GameResult.BLACK_WIN
            "1/2-1/2" -> GameResult.DRAW
            "*" -> null
            else -> throw PgnParseException("Invalid $context result '$marker'", index, marker)
        }

        private fun symbolicNag(symbol: String): Nag? = when (symbol) {
            "!" -> Nag(1)
            "?" -> Nag(2)
            "!!" -> Nag(3)
            "??" -> Nag(4)
            "!?" -> Nag(5)
            "?!" -> Nag(6)
            else -> null
        }

        private fun splitAttachedNag(symbol: String): Pair<String, Nag?> {
            for (suffix in listOf("!!", "??", "!?", "?!", "!", "?")) {
                if (symbol.length > suffix.length && symbol.endsWith(suffix)) {
                    return symbol.dropLast(suffix.length) to symbolicNag(suffix)
                }
            }
            return symbol to null
        }

        private fun plyFor(position: Position): Int =
            2 * (position.fullmoveNumber - 1) + if (position.sideToMove == Color.WHITE) 1 else 2

        private fun headerIndex(name: String, entries: List<HeaderEntry>): Int =
            entries.firstOrNull { it.name == name }?.index ?: 0

        private fun requireToken(type: PgnTokenType, message: String): PgnToken {
            val token = peek() ?: throw PgnParseException(message, sourceLength)
            if (token.type != type) throw PgnParseException(message, token.index, token.text)
            return consume()
        }

        private fun atEnd(): Boolean = cursor >= tokens.size
        private fun peek(): PgnToken? = tokens.getOrNull(cursor)
        private fun peekType(): PgnTokenType? = peek()?.type
        private fun consume(): PgnToken = tokens[cursor++]
    }

    private class Writer(private val game: GameTree) {
        private val words = ArrayList<String>()

        fun write(): String {
            val headers = canonicalHeaders()
            val headerText = headers.entries.joinToString("\n") { (name, value) ->
                "[$name \"${escapeTagValue(value)}\"]"
            }

            for (comment in game.rootComments) words += commentToken(comment)
            emitFromParent(game.rootId)
            words += resultMarker(game.result)

            return buildString {
                if (headerText.isNotEmpty()) {
                    append(headerText)
                    append("\n\n")
                }
                append(words.joinToString(" "))
                append('\n')
            }
        }

        private fun canonicalHeaders(): LinkedHashMap<String, String> {
            val normalized = LinkedHashMap(game.headers)
            normalized["Result"] = resultMarker(game.result)

            if (game.startPosition.variant == Variant.CHESS960) {
                normalized["Variant"] = "Chess960"
                normalized["SetUp"] = "1"
                normalized["FEN"] = Fen.serialize(game.startPosition)
            } else if (game.startPosition == Position.initial()) {
                normalized.remove("Variant")
                normalized.remove("SetUp")
                normalized.remove("FEN")
            } else {
                normalized.remove("Variant")
                normalized["SetUp"] = "1"
                normalized["FEN"] = Fen.serialize(game.startPosition)
            }

            val ordered = LinkedHashMap<String, String>()
            val preferred = listOf(
                "Event", "Site", "Date", "Round", "White", "Black", "Result",
                "Variant", "SetUp", "FEN",
            )
            for (key in preferred) normalized[key]?.let { ordered[key] = it }
            normalized.keys
                .filterNot { it in preferred }
                .sorted()
                .forEach { ordered[it] = normalized.getValue(it) }
            return ordered
        }

        /** Emits the main child, all of its sibling alternatives, then its main continuation. */
        private fun emitFromParent(parentId: GameNodeId) {
            val children = game.childrenOf(parentId)
            if (children.isEmpty()) return

            val main = children.first()
            emitNode(parentId, main)
            for (variation in children.drop(1)) {
                words += "("
                emitSelected(parentId, variation)
                words += ")"
            }
            emitFromParent(main.id)
        }

        /** Emits one selected variation without re-emitting its siblings at the same branch point. */
        private fun emitSelected(parentId: GameNodeId, node: GameNode) {
            emitNode(parentId, node)
            emitFromParent(node.id)
        }

        private fun emitNode(parentId: GameNodeId, node: GameNode) {
            val before = game.node(parentId).position
            words += if (before.sideToMove == Color.WHITE) {
                "${before.fullmoveNumber}."
            } else {
                "${before.fullmoveNumber}..."
            }
            node.leadingComments.forEach { words += commentToken(it) }
            words += node.san ?: San.generate(before, requireNotNull(node.move))
            node.nags.forEach { words += "$${it.value}" }
            node.comments.forEach { words += commentToken(it) }
        }

        private fun commentToken(comment: String): String {
            require('}' !in comment) { "PGN brace comments cannot contain '}'" }
            return "{$comment}"
        }

        private fun escapeTagValue(value: String): String = buildString(value.length) {
            for (char in value) {
                when (char) {
                    '\\', '"' -> append('\\').append(char)
                    '\r', '\n' -> append(' ')
                    else -> append(char)
                }
            }
        }
    }

    private fun resultMarker(result: GameResult?): String = when (result) {
        GameResult.WHITE_WIN -> "1-0"
        GameResult.BLACK_WIN -> "0-1"
        GameResult.DRAW -> "1/2-1/2"
        null -> "*"
    }
}
