package dev.lumenchess.engine.host.transport;

oneway interface IEngineHostCallback {
    void onSearchResult(
        String sessionId,
        long hostGeneration,
        long searchId,
        long positionRevision,
        String bestMoveUci
    );
    void onSearchInfo(
        String sessionId,
        long hostGeneration,
        long searchId,
        long positionRevision,
        int depth,
        int scoreKind,
        int scoreValue,
        int scoreBound,
        long nodes,
        long nodesPerSecond,
        String principalVariation
    );
    void onHostFailure(
        String sessionId,
        long hostGeneration,
        int code,
        String message
    );
}
