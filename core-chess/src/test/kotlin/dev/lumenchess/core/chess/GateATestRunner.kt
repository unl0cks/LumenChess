package dev.lumenchess.core.chess

private fun assertEquals(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) error("$message: expected=$expected actual=$actual")
}

private fun assertTrue(value: Boolean, message: String) {
    if (!value) error(message)
}

private inline fun <reified T : Throwable> assertFails(message: String, block: () -> Unit) {
    try {
        block()
    } catch (t: Throwable) {
        if (t is T) return
        error("$message: expected ${T::class.simpleName}, got ${t::class.simpleName}: ${t.message}")
    }
    error("$message: expected ${T::class.simpleName}, but no exception was thrown")
}

private fun testSquareRoundTrip() {
    for (index in 0 until 64) {
        val square = Square.fromIndex(index)
        assertEquals(index, Square.parse(square.algebraic).index, "square algebraic round trip")
    }
}


private fun testPositionDefensivelyCopiesBoard() {
    val mutable = Position.initial().board.toMutableList()
    val position = Position(
        board = mutable,
        sideToMove = Color.WHITE,
        castlingRights = CastlingRights(),
        enPassantSquare = null,
        halfmoveClock = 0,
        fullmoveNumber = 1,
    )
    val before = position.repetitionKey
    mutable[Square.parse("a1").index] = null
    assertTrue(position[Square.parse("a1")] != null, "Position must defensively copy caller-owned board storage")
    assertEquals(before, position.repetitionKey, "external board mutation must not alter position key")
}

private fun testInitialFenRoundTripAndKey() {
    val initial = Position.initial()
    assertEquals(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        Fen.serialize(initial),
        "initial FEN",
    )
    val reparsed = Fen.parse(Fen.serialize(initial))
    assertEquals(initial, reparsed, "FEN round trip")
    assertEquals(initial.repetitionKey, reparsed.repetitionKey, "deterministic repetition key")
}

private fun testMalformedFenRejected() {
    val invalid = listOf(
        "8/8/8/8/8/8/8/8 w - - 0 1", // no kings
        "8/8/8/8/8/8/8/K6k x - - 0 1", // bad side
        "8/8/8/8/8/8/8/K6k w Z - 0 1", // bad castling
        "8/8/8/8/8/8/8/K6k w - e4 0 1", // impossible EP rank
        "4k3/8/8/8/8/8/8/4K3 w K - 0 1", // castling right without rook
        "8/8/8/8/8/8/8/K6k w - - -1 1", // negative halfmove
    )
    invalid.forEach { fen ->
        assertFails<IllegalArgumentException>("invalid FEN must fail: $fen") { Fen.parse(fen) }
    }
}

private fun testStartPositionPerft() {
    val position = Position.initial()
    val expected = mapOf(1 to 20L, 2 to 400L, 3 to 8902L, 4 to 197281L, 5 to 4865609L)
    expected.forEach { (depth, nodes) ->
        assertEquals(nodes, Perft.count(position, depth), "start position perft depth $depth")
    }
}

private fun testKiwipetePerft() {
    val position = Fen.parse("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
    val expected = mapOf(1 to 48L, 2 to 2039L, 3 to 97862L, 4 to 4085603L)
    expected.forEach { (depth, nodes) ->
        assertEquals(nodes, Perft.count(position, depth), "kiwipete perft depth $depth")
    }
}


private fun testAdditionalStandardPerftSuite() {
    val cases = listOf(
        Triple(
            "perft position 3",
            Fen.parse("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"),
            mapOf(1 to 14L, 2 to 191L, 3 to 2812L, 4 to 43238L),
        ),
        Triple(
            "perft position 5",
            Fen.parse("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"),
            mapOf(1 to 44L, 2 to 1486L, 3 to 62379L, 4 to 2103487L),
        ),
        Triple(
            "perft position 6",
            Fen.parse("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"),
            mapOf(1 to 46L, 2 to 2079L, 3 to 89890L, 4 to 3894594L),
        ),
    )
    for ((name, position, expected) in cases) {
        for ((depth, nodes) in expected) {
            assertEquals(nodes, Perft.count(position, depth), "$name depth $depth")
        }
    }
}

private fun testEnPassantThatExposesKingIsIllegal() {
    val position = Fen.parse("4k3/8/8/r4pPK/8/8/8/8 w - f6 0 1")
    val illegalEp = Move(Square.parse("g5"), Square.parse("f6"))
    assertTrue(illegalEp !in MoveGenerator.legalMoves(position), "en passant exposing rook check must be illegal")
}

private fun testCastlingThroughAttackIsIllegal() {
    val position = Fen.parse("r3k2r/8/8/8/2b5/8/8/R3K2R w KQkq - 0 1")
    val castleKingSide = Move(Square.parse("e1"), Square.parse("g1"))
    assertTrue(castleKingSide !in MoveGenerator.legalMoves(position), "king may not castle through attacked f1")
}

private fun testPromotionGeneratesFourChoices() {
    val position = Fen.parse("7k/P7/8/8/8/8/8/7K w - - 0 1")
    val promotions = MoveGenerator.legalMoves(position).filter {
        it.from == Square.parse("a7") && it.to == Square.parse("a8")
    }
    assertEquals(setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT), promotions.mapNotNull { it.promotion }.toSet(), "promotion set")
}

private fun testCheckmateAndStalemate() {
    val mate = Fen.parse("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1")
    assertEquals(Termination.CHECKMATE, Rules.termination(mate), "checkmate detection")

    val stalemate = Fen.parse("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
    assertEquals(Termination.STALEMATE, Rules.termination(stalemate), "stalemate detection")
}

private fun testDrawRules() {
    val bareKings = Fen.parse("8/8/8/8/8/8/5k2/7K w - - 0 1")
    assertTrue(Rules.isInsufficientMaterial(bareKings), "bare kings are insufficient material")

    val bishop = Fen.parse("8/8/8/8/8/8/5k2/2B4K w - - 0 1")
    assertTrue(Rules.isInsufficientMaterial(bishop), "king+bishop vs king is insufficient material")

    val fifty = Fen.parse("8/8/8/8/8/8/5k2/7K w - - 100 51")
    val seventyFive = Fen.parse("8/8/8/8/8/8/5k2/7K w - - 150 76")
    assertTrue(Rules.drawStatus(fifty, GameHistory(fifty)).claimableFiftyMove, "50-move claim")
    assertTrue(Rules.drawStatus(seventyFive, GameHistory(seventyFive)).automaticSeventyFiveMove, "75-move automatic draw")
}

private fun testThreefoldAndFivefoldRepetition() {
    var position = Position.initial()
    val history = GameHistory(position)
    val cycle = listOf("g1f3", "g8f6", "f3g1", "f6g8")

    repeat(2) {
        for (uci in cycle) {
            val move = Move.parseUci(uci)
            position = MoveGenerator.applyLegalMove(position, move)
            history.record(position)
        }
    }
    assertTrue(Rules.drawStatus(position, history).claimableThreefold, "third occurrence should be claimable")

    repeat(2) {
        for (uci in cycle) {
            val move = Move.parseUci(uci)
            position = MoveGenerator.applyLegalMove(position, move)
            history.record(position)
        }
    }
    assertTrue(Rules.drawStatus(position, history).automaticFivefold, "fifth occurrence should be automatic")
}

private fun testApplyLegalMoveRejectsIllegalMove() {
    val position = Position.initial()
    assertFails<IllegalArgumentException>("illegal runtime move must be rejected") {
        MoveGenerator.applyLegalMove(position, Move.parseUci("e2e5"))
    }
}

fun main() {
    val tests = listOf(
        ::testSquareRoundTrip,
        ::testPositionDefensivelyCopiesBoard,
        ::testInitialFenRoundTripAndKey,
        ::testMalformedFenRejected,
        ::testStartPositionPerft,
        ::testKiwipetePerft,
        ::testAdditionalStandardPerftSuite,
        ::testEnPassantThatExposesKingIsIllegal,
        ::testCastlingThroughAttackIsIllegal,
        ::testPromotionGeneratesFourChoices,
        ::testCheckmateAndStalemate,
        ::testDrawRules,
        ::testThreefoldAndFivefoldRepetition,
        ::testApplyLegalMoveRejectsIllegalMove,
    )

    var passed = 0
    tests.forEach { test ->
        test()
        passed++
        println("PASS ${test.name}")
    }
    println("Gate A core tests: $passed/${tests.size} passed")
}
