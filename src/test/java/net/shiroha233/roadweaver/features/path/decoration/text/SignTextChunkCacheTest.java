package net.shiroha233.roadweaver.features.path.decoration.text;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.persistence.files.SignTextFileStorage.PendingSignText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignTextChunkCacheTest {
    private static final int MAX_CHUNKS = 32;

    @Test
    void everyLoadReachesItsPollWhenMoreChunksWaitThanTheCacheHolds() {
        // The test world had 75 dirty chunks cycling through a 32 chunk cache: no poll ever found its rows.
        SignTextChunkCache cache = new SignTextChunkCache(MAX_CHUNKS);
        int chunks = 75;
        for (int i = 0; i < chunks; i++) {
            cache.putLoaded(key(i), List.of(row(i)));
        }
        for (int i = 0; i < chunks; i++) {
            List<PendingSignText> rows = cache.get(key(i), 32);
            assertNotNull(rows, "chunk " + i);
            assertEquals(row(i).id(), rows.getFirst().id());
        }
    }

    @Test
    void polledChunksStayBoundedByTheLru() {
        SignTextChunkCache cache = new SignTextChunkCache(MAX_CHUNKS);
        for (int i = 0; i < MAX_CHUNKS + 1; i++) {
            cache.putLoaded(key(i), List.of(row(i)));
            cache.get(key(i), 32);
        }
        assertNull(cache.get(key(0), 32));
        assertNotNull(cache.get(key(MAX_CHUNKS), 32));
    }

    @Test
    void unloadedChunkPollsNull() {
        assertNull(new SignTextChunkCache(MAX_CHUNKS).get(key(0), 32));
    }

    @Test
    void invalidateDropsTheStaleCopy() {
        SignTextChunkCache cache = new SignTextChunkCache(MAX_CHUNKS);
        cache.putLoaded(key(0), List.of());
        assertTrue(cache.get(key(0), 32).isEmpty());

        cache.invalidate(key(0));
        assertNull(cache.get(key(0), 32));

        cache.putLoaded(key(1), List.of(row(1)));
        cache.invalidate(key(1));
        assertNull(cache.get(key(1), 32));
    }

    @Test
    void writtenRowsAreRemovedBeforeAndAfterThePoll() {
        SignTextChunkCache cache = new SignTextChunkCache(MAX_CHUNKS);
        PendingSignText first = row(0);
        PendingSignText second = new PendingSignText(new BlockPos(1, 70, 1).asLong(), new BlockPos(1, 70, 1), 0, "20", 0L);

        cache.putLoaded(key(0), List.of(first, second));
        cache.removeIds(key(0), List.of(first.id()));
        assertEquals(List.of(second), cache.get(key(0), 32));

        cache.removeIds(key(0), List.of(second.id()));
        assertTrue(cache.get(key(0), 32).isEmpty());
    }

    @Test
    void pollReturnsAtMostTheLimitAsACopy() {
        SignTextChunkCache cache = new SignTextChunkCache(MAX_CHUNKS);
        PendingSignText second = new PendingSignText(new BlockPos(2, 70, 2).asLong(), new BlockPos(2, 70, 2), 0, "30", 0L);
        cache.putLoaded(key(0), List.of(row(0), second));

        List<PendingSignText> rows = cache.get(key(0), 1);
        assertEquals(1, rows.size());
        rows.clear();
        assertEquals(2, cache.get(key(0), 32).size());
    }

    private static long key(int i) {
        return ChunkPos.pack(i, -i);
    }

    private static PendingSignText row(int i) {
        BlockPos pos = new BlockPos(i * 16, 64, -i * 16);
        return new PendingSignText(pos.asLong(), pos, 0, Integer.toString(i), 0L);
    }
}
