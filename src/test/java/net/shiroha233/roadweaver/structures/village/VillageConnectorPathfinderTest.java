package net.shiroha233.roadweaver.structures.village;

import net.shiroha233.roadweaver.structures.village.VillageConnectorPathfinder.Column;
import net.shiroha233.roadweaver.structures.village.VillageConnectorPathfinder.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageConnectorPathfinderTest {
    @Test
    void walksStraightToTheStreet() {
        List<Column> path = VillageConnectorPathfinder.find(new Column(0, 0),
                List.of(new Rect(10, -5, 12, 5)), List.of(), null, 8, 10_000);
        assertEquals(new Column(0, 0), path.getFirst());
        assertEquals(new Column(9, 0), path.getLast());
        assertEquals(10, path.size());
    }

    @Test
    void goesAroundABuildingInTheWay() {
        Rect house = new Rect(4, -6, 7, 6);
        List<Column> path = VillageConnectorPathfinder.find(new Column(0, 0),
                List.of(new Rect(12, -20, 13, 20)), List.of(house.grow(1)), null, 12, 50_000);
        assertFalse(path.isEmpty());
        for (Column column : path) {
            assertFalse(house.grow(1).contains(column.x(), column.z()), "connector enters the building at " + column);
        }
        assertEquals(11, path.getLast().x());
    }

    @Test
    void picksTheNearestStreet() {
        List<Column> path = VillageConnectorPathfinder.find(new Column(0, 0),
                List.of(new Rect(30, 0, 31, 0), new Rect(-6, -2, -5, 2)), List.of(), null, 8, 10_000);
        assertEquals(new Column(-4, 0), path.getLast());
    }

    @Test
    void avoidsCliffs() {
        // A 6 block wall at x = 5 except for a gap at z = 8.
        List<Column> path = VillageConnectorPathfinder.find(new Column(0, 0),
                List.of(new Rect(10, -10, 11, 10)), List.of(),
                (x, z) -> x >= 5 && z != 8 ? 6 : 0, 12, 50_000);
        assertFalse(path.isEmpty());
        assertTrue(path.stream().anyMatch(c -> c.x() == 5 && c.z() == 8), "path should use the gap: " + path);
    }

    @Test
    void returnsNothingWhenWalledIn() {
        List<Rect> walls = List.of(new Rect(-3, -3, 3, -3), new Rect(-3, 3, 3, 3), new Rect(-3, -3, -3, 3), new Rect(3, -3, 3, 3));
        List<Column> path = VillageConnectorPathfinder.find(new Column(0, 0),
                List.of(new Rect(20, 0, 21, 0)), walls, null, 4, 10_000);
        assertTrue(path.isEmpty());
    }
}
