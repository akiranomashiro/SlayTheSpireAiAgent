package com.example.aiagent;

import java.util.*;

// 地图节点类
class MapNode {
    private int row;          // 节点行坐标
    private int col;          // 节点列坐标
    private char nodeType;    // 节点类型（如 M/R/$ 等）
    private List<MapNode> neighbors; // 相邻节点列表

    public MapNode(int row, int col, char nodeType) {
        this.row = row;
        this.col = col;
        this.nodeType = nodeType;
        this.neighbors = new ArrayList<>();
    }

    // 添加相邻节点
    public void addNeighbor(MapNode neighbor) {
        neighbors.add(neighbor);
    }

    // 获取节点信息
    public String getNodeInfo() {
        return String.format("坐标(%d,%d)，类型：%c，相邻节点数：%d",
                row, col, nodeType, neighbors.size());
    }
}
