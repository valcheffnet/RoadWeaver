package net.shiroha233.roadweaver.features.path.decoration.text;

import net.shiroha233.roadweaver.persistence.files.SignTextFileStorage.PendingSignText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Queued sign text read from disk, per chunk. Not thread-safe: {@link SignTextFileIo} calls it under its lock.
 *
 * <p>A finished load waits in {@code loaded} until its chunk polls it, and only then joins the bounded LRU. When the
 * loads went straight into the LRU, more than {@code maxChunks} dirty chunks evicted each other's rows before their
 * turn came round, every poll missed and started another load, and the queued text was never written.
 */
final class SignTextChunkCache {
    private final LinkedHashMap<Long, List<PendingSignText>> recent;
    private final Map<Long, List<PendingSignText>> loaded = new HashMap<>();

    SignTextChunkCache(int maxChunks) {
        recent = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, List<PendingSignText>> eldest) {
                return size() > maxChunks;
            }
        };
    }

    /** @return a copy of up to {@code limit} rows, or {@code null} when the chunk has not been loaded */
    List<PendingSignText> get(long chunkKey, int limit) {
        List<PendingSignText> rows = recent.get(chunkKey);
        if (rows == null) {
            rows = loaded.remove(chunkKey);
            if (rows == null) return null;
            recent.put(chunkKey, rows);
        }
        return rows.size() <= limit ? new ArrayList<>(rows) : new ArrayList<>(rows.subList(0, limit));
    }

    void putLoaded(long chunkKey, List<PendingSignText> rows) {
        recent.remove(chunkKey);
        loaded.put(chunkKey, new ArrayList<>(rows));
    }

    void removeIds(long chunkKey, Collection<Long> ids) {
        removeIds(recent.get(chunkKey), ids);
        removeIds(loaded.get(chunkKey), ids);
    }

    private static void removeIds(List<PendingSignText> rows, Collection<Long> ids) {
        if (rows != null) {
            rows.removeIf(row -> row != null && ids.contains(row.id()));
        }
    }

    void invalidate(long chunkKey) {
        recent.remove(chunkKey);
        loaded.remove(chunkKey);
    }

    void clear() {
        recent.clear();
        loaded.clear();
    }
}
