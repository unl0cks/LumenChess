package dev.lumenchess.engine.host.transport;

oneway interface IEngineHostCallback {
    void onSearchResult(
        String sessionId,
        long hostGeneration,
        long searchId,
        long positionRevision,
        String bestMoveUci
    );
    void onHostFailure(
        String sessionId,
        long hostGeneration,
        int code,
        String message
    );
}
