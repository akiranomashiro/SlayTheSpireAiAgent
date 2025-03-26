package com.example.aiagent;

import java.util.ArrayList;
import java.util.List;

// 定义游戏角色的数据结构类
class GameCharacter {
    // 当前生命
    private int currentHealth;
    // 最大生命
    private int maxHealth;
    // 当前层数
    private int currentFloor;
    // 层底 boss
    private String floorBoss;
    // 当前遗物列表
    private List<Integer> relicList;
    // 当前药水列表
    private List<Integer> potionList;
    // 当前卡牌列表
    private List<Integer> cardList;
    // 当前钱财
    private int money;
    // 当前地图
    private MapGraph currentMap;

    // 构造函数，用于初始化游戏角色的信息
    public GameCharacter(int currentHealth, int maxHealth, int currentFloor, String floorBoss,
                         List<Integer> relicList, List<Integer> potionList, List<Integer> cardList, int money) {
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
        this.currentFloor = currentFloor;
        this.floorBoss = floorBoss;
        this.relicList = new ArrayList<>(relicList);
        this.potionList = new ArrayList<>(potionList);
        this.cardList = new ArrayList<>(cardList);
        this.money = money;
    }

    // 获取当前生命
    public int getCurrentHealth() {
        return currentHealth;
    }

    // 设置当前生命
    public void setCurrentHealth(int currentHealth) {
        this.currentHealth = currentHealth;
    }

    // 获取最大生命
    public int getMaxHealth() {
        return maxHealth;
    }

    // 设置最大生命
    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    // 获取当前层数
    public int getCurrentFloor() {
        return currentFloor;
    }

    // 设置当前层数
    public void setCurrentFloor(int currentFloor) {
        this.currentFloor = currentFloor;
    }

    // 获取层底 boss
    public String getFloorBoss() {
        return floorBoss;
    }

    // 设置层底 boss
    public void setFloorBoss(String floorBoss) {
        this.floorBoss = floorBoss;
    }

    // 获取当前遗物列表
    public List<Integer> getRelicList() {
        return new ArrayList<>(relicList);
    }

    // 设置当前遗物列表
    public void setRelicList(List<Integer> relicList) {
        this.relicList = new ArrayList<>(relicList);
    }

    // 获取当前药水列表
    public List<Integer> getPotionList() {
        return new ArrayList<>(potionList);
    }

    // 设置当前药水列表
    public void setPotionList(List<Integer> potionList) {
        this.potionList = new ArrayList<>(potionList);
    }

    // 获取当前卡牌列表
    public List<Integer> getCardList() {
        return new ArrayList<>(cardList);
    }

    // 设置当前卡牌列表
    public void setCardList(List<Integer> cardList) {
        this.cardList = new ArrayList<>(cardList);
    }

    // 获取当前钱财
    public int getMoney() {
        return money;
    }

    // 设置当前钱财
    public void setMoney(int money) {
        this.money = money;
    }

    public MapGraph getCurrentMap() {
        return currentMap;
    }

    public void setCurrentMap(MapGraph currentMap) {
        this.currentMap = currentMap;
    }

    @Override
    public String toString() {
        return "GameCharacter{" +
                "currentHealth=" + currentHealth +
                ", maxHealth=" + maxHealth +
                ", currentFloor=" + currentFloor +
                ", floorBoss='" + floorBoss + '\'' +
                ", relicList=" + relicList +
                ", potionList=" + potionList +
                ", cardList=" + cardList +
                ", money=" + money +
                '}';
    }
}
