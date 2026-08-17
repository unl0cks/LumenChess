package dev.lumenchess.engine.host.transport;

import dev.lumenchess.engine.host.transport.IEngineHostCallback;

interface IEngineHost {
    long getHostGeneration();
    int getProcessId();
    String openSession(String requestedSessionId, String engineId, IEngineHostCallback callback);
    void newGame(String sessionId);
    void startSearch(
        String sessionId,
        long searchId,
        long positionRevision,
        String fen,
        String variant,
        int depth,
        long nodes,
        long moveTimeMillis,
        int multiPv,
        String strengthModel,
        int targetElo,
        long strengthSeed
    );
    void stopSearch(String sessionId, long searchId);
    void closeSession(String sessionId);
    void shutdownHost();
}
