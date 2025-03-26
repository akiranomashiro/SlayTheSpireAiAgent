package com.example.aiagent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class MapGraph {
    private final Map<String, MapNode> nodeMap; // 存储所有节点，键为坐标字符串（行_列）

    public MapGraph() {
        this.nodeMap = new HashMap<>();
    }

    // 添加节点
    public void addNode(int row, int col, char nodeType) {
        String key = row + "_" + col;
        if (!nodeMap.containsKey(key)) {
            nodeMap.put(key, new MapNode(row, col, nodeType));
        }
    }

    // 添加边（连接两个节点）
    public void addEdge(int row1, int col1, int row2, int col2) {
        String key1 = row1 + "_" + col1;
        String key2 = row2 + "_" + col2;
        if (nodeMap.containsKey(key1) && nodeMap.containsKey(key2)) {
            MapNode node1 = nodeMap.get(key1);
            MapNode node2 = nodeMap.get(key2);
            node1.addNeighbor(node2);
            node2.addNeighbor(node1); // 无向图双向添加
        }
    }

    public Map<String, MapNode> getAllNodesMap() {
        return nodeMap;
    }

    // 获取所有节点
    public Collection<MapNode> getNodes() {
        return nodeMap.values();
    }
}
