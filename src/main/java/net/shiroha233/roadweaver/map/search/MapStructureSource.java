/* 文件职责：统一表示地图结构的来源，供搜索结果和地图筛选使用。 */
package net.shiroha233.roadweaver.map.search;

public enum MapStructureSource {
    UNKNOWN(-1),
    PREDICTED(0),
    MANUAL(1);

    private final int id;

    MapStructureSource(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static MapStructureSource fromId(int id) {
        for (MapStructureSource source : values()) {
            if (source.id == id) return source;
        }
        return UNKNOWN;
    }
}
