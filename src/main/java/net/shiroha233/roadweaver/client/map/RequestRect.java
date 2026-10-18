package net.shiroha233.roadweaver.client.map;

/** Block rectangle of a map snapshot request; shared by the map screen and the server codec. */
public record RequestRect(int minX, int minZ, int maxX, int maxZ) {
    public RequestRect {
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("invalid request rect");
        }
    }

    public boolean contains(RequestRect other) {
        return other != null
                && other.minX >= minX
                && other.maxX <= maxX
                && other.minZ >= minZ
                && other.maxZ <= maxZ;
    }

    public boolean intersects(RequestRect other) {
        return other != null
                && maxX >= other.minX
                && minX <= other.maxX
                && maxZ >= other.minZ
                && minZ <= other.maxZ;
    }
}
