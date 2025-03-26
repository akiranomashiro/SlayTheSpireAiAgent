package com.example.aiagent;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ADBDeviceLogMonitor {
    private Process adbProcess;
    private BufferedReader reader;
    private Thread logReadingThread;
    private String targetTag = "Android Application Output";
    private GameCharacter gameCharacter;
    private Map<String, Integer> cardMap = new HashMap<>();
    // 状态标记
    private boolean isReadingInitialCards = false;
    private boolean isGeneratingMap = false;
    private boolean isSelectingCard = false;
    private int consecutiveAlreadySeen = 0;
    private List<String> mapLines = new ArrayList<>();
    private ActionExecutor actionExecutor;
    public ADBDeviceLogMonitor() {
    }

    public void startMonitoring() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("adb", "logcat", "-s", targetTag);
            adbProcess = processBuilder.start();
            reader = new BufferedReader(new InputStreamReader(adbProcess.getInputStream()));

            logReadingThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        handleLogLine(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            logReadingThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopMonitoring() {
        if (adbProcess != null) adbProcess.destroy();
        if (logReadingThread != null) logReadingThread.interrupt();
        try { if (reader != null) reader.close(); } catch (IOException e) { e.printStackTrace(); }
    }

    private void handleLogLine(String line) {
        // 处理BOSS名称
        if (line.contains("info [BOSS]")) {
            String bossName = line.split("info \\[BOSS\\] ")[1].trim();
            if (gameCharacter != null) {
                gameCharacter.setFloorBoss(bossName);
            }
        }

        // 处理初始手牌
        else if (line.contains("info Cardpool load time")) {
            isReadingInitialCards = true;
        } else if (line.contains("info Content generation time")) {
            isReadingInitialCards = false;
        } else if (isReadingInitialCards && line.contains("info Already seen:")) {
            cardMap.put("Strike_R", 1);
            cardMap.put("Defend_R", 2);
            cardMap.put("Bash", 3);
            String cardName = line.split("info Already seen: ")[1].trim();
            if (gameCharacter != null) {
                int cardId = cardMap.get(cardName);
                gameCharacter.getCardList().add(cardId);
            }
        }

        // 处理地图生成
        else if (line.contains("info Generated the following dungeon map:")) {
            isGeneratingMap = true;
            mapLines.clear();
        } else if (line.contains("info Map generation time")) {
            isGeneratingMap = false;
            parseMapLines(mapLines);
        } else if (isGeneratingMap) {
            if (!line.trim().isEmpty() && !line.contains("info")) {
                mapLines.add(line);
            }
        }

        // 处理选牌逻辑
        else if (line.contains("info Already seen:")) {
            consecutiveAlreadySeen++;
            if (consecutiveAlreadySeen >= 2) {
                isSelectingCard = true;
            }
        } else {
            consecutiveAlreadySeen = 0;
        }

        if (isSelectingCard && line.contains("info Obtained")) {
            String cardName = line.split("info Obtained ")[1].split(" uniquetempvar")[0].trim();
            if (gameCharacter != null) {
                int cardId = cardMap.get(cardName);
                gameCharacter.getCardList().add(cardId);
            }
            isSelectingCard = false;
        }

        // 处理遗物识别（需实现深度学习模块）
        else if (line.contains("info Returning RARE relic")) {
            // 示例：假设识别结果为"123"，实际需调用深度学习模块
            String relicId = "123"; // deepLearningModule.recognizeRelic();
            if (relicId != null) {
                int index = Integer.parseInt(relicId);
                if (index < gameCharacter.getRelicList().size()) {
                    gameCharacter.getRelicList().set(index, 1);
                }
            }
        }

        actionExecutor = new ActionExecutor();

        // 获取 Python 解释器
        Python py = Python.getInstance();

        // 导入 Python 模块
        PyObject pyModule = py.getModule("dqn-atari-cnn");

        // 获取动作
        PyObject getActionFunction = pyModule.callAttr("push");


        int action = getActionFunction.toJava(Integer.class);
        actionExecutor.executeAction(action);
    }

    private void parseMapLines(List<String> lines) {
        MapGraph mapGraph = new MapGraph();
        int y = -1;
        Pattern linePattern = Pattern.compile("^(\\d+)\\s+");

        for (String line : lines) {
            Matcher matcher = linePattern.matcher(line);
            if (matcher.find()) {
                y = Integer.parseInt(matcher.group(1));
                String mapPart = line.substring(matcher.end()).trim();

                for (int i = 0; i < mapPart.length(); i++) {
                    char c = mapPart.charAt(i);
                    if (c != ' ') {
                        int x = i / 2;
                        mapGraph.addNode(y, x, c);
                        addAdjacentEdges(mapGraph, y, x);
                    }
                }
            }
        }
        if (gameCharacter != null) {
            gameCharacter.setCurrentMap(mapGraph);
        }
    }

    private void addAdjacentEdges(MapGraph mapGraph, int y, int x) {
        String currentKey = y + "_" + x;
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : directions) {
            int newY = y + dir[0];
            int newX = x + dir[1];
            String neighborKey = newY + "_" + newX;
            // 使用 getAllNodesMap() 替代 getNodeMap()
            if (mapGraph.getAllNodesMap().containsKey(neighborKey)) {
                mapGraph.addEdge(y, x, newY, newX);
            }
        }
    }

    public void setGameCharacter(GameCharacter gameCharacter) {
        this.gameCharacter = gameCharacter;
    }
}