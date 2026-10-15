/* 文件职责：验证各寻路器会跳过已被更优节点替换的优先队列条目。 */
package net.shiroha233.roadweaver.pathfinding.impl;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;

class PathfinderQueueDeduplicationTest {
    @Test
    void singleSourcePathfindersPollPastSupersededQueueHead() throws Exception {
        assertPollsActiveNode(BasicAStarPathfinder.class, "f");
        assertPollsActiveNode(GradientDescentPathfinder.class, "fCost");
        assertPollsActiveNode(PotentialFieldPathfinder.class, "f");
    }

    @Test
    void bidirectionalPathfinderPollsPastSupersededQueueHead() throws Exception {
        assertPollsActiveNode(BidirectionalAStarPathfinder.class, "f");
    }

    private static void assertPollsActiveNode(Class<?> pathfinderClass, String scoreFieldName) throws Exception {
        Class<?> nodeClass = nestedClass(pathfinderClass, "Node");
        Constructor<?> constructor = nodeClass.getDeclaredConstructor(
                BlockPos.class, nodeClass, double.class, double.class);
        constructor.setAccessible(true);

        BlockPos supersededPosition = new BlockPos(0, 64, 0);
        Object stale = constructor.newInstance(supersededPosition, null, 20.0, 10.0);
        Object replacement = constructor.newInstance(supersededPosition, null, 10.0, 30.0);
        Object active = constructor.newInstance(new BlockPos(8, 64, 0), null, 15.0, 20.0);
        Field scoreField = nodeClass.getDeclaredField(scoreFieldName);
        scoreField.setAccessible(true);

        PriorityQueue<Object> open = new PriorityQueue<>(
                (left, right) -> Double.compare(readDouble(scoreField, left), readDouble(scoreField, right)));
        open.add(stale);
        open.add(active);
        open.add(replacement);

        Map<BlockPos, Object> currentNodes = new HashMap<>();
        currentNodes.put(supersededPosition, replacement);
        currentNodes.put(new BlockPos(8, 64, 0), active);

        Method pollActive = pathfinderClass.getDeclaredMethod(
                "pollActiveOpenNode", PriorityQueue.class, Map.class, Set.class);
        pollActive.setAccessible(true);

        assertSame(active, pollActive.invoke(null, open, currentNodes, Set.of()));
    }

    private static Class<?> nestedClass(Class<?> owner, String simpleName) {
        for (Class<?> candidate : owner.getDeclaredClasses()) {
            if (candidate.getSimpleName().equals(simpleName)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Missing nested class: " + simpleName);
    }

    private static double readDouble(Field field, Object target) {
        try {
            return field.getDouble(target);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
