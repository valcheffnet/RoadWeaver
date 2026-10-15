/* 文件职责：描述道路持久化层的旧指纹与新道路替换关系。 */
package net.shiroha233.roadweaver.persistence;

import net.shiroha233.roadweaver.core.model.RoadData;

/** 一条道路替换操作的不可变值对象。 */
public record RoadReplacement(long oldFingerprint, RoadData newRoad) {
}
