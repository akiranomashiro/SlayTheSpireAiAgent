import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np
import random
from collections import deque
from java import jclass

# 加载 Java 类
MainActivity = jclass('com.example.aiagent.MainActivity')
GameCharacter = jclass('com.example.aiagent.GameCharacter')
MapGraph = jclass('com.example.aiagent.MapGraph')
MapNode = jclass('com.example.aiagent.MapNode')

# 创建 MainActivity 实例
main_activity = MainActivity()

def get_main_activity(context):
    return context

node_dict = {
    "M": 1,
    "?": 2,
    "E": 3,
    "$": 4,
    "R": 5,
    "T": 6,
}

act = 0

# 超参数配置
CONFIG = {
    "embed_dim": 32,
    "hidden_dim": 256,
    "batch_size": 128,
    "buffer_size": 100000,
    "gamma": 0.99,
    "lr": 1e-4,
    "epsilon_start": 1.0,
    "epsilon_end": 0.01,
    "epsilon_decay": 0.995,
    "target_update": 100
}


class StateEncoder(torch.nn.Module):
    """游戏状态编码器"""
    def __init__(self):
        super().__init__()
        # 卡牌嵌入
        self.card_embed = torch.nn.Embedding(1001, CONFIG["embed_dim"])
        # 药水嵌入
        self.potion_embed = torch.nn.Embedding(51, 8)
        # 节点类型嵌入
        self.node_embed = torch.nn.Embedding(10, 6)
        # Boss嵌入
        self.boss_embed = torch.nn.Embedding(20, 16)

    def forward(self, state):
        # 解包原始状态
        collection = torch.LongTensor(state["collection"])
        card_ids = torch.LongTensor(state["card_deck"])
        potion_ids = torch.LongTensor(state["potion"])
        path_nodes = torch.LongTensor(state["path_nodes"])
        current_links = torch.LongTensor(state["current_links"])
        next_links = torch.LongTensor(state["next_links"])
        boss_id = torch.LongTensor([state["boss_id"]])
        current_health = torch.LongTensor([state["current_health"]])
        max_health = torch.LongTensor([state["max_health"]])
        current_floor = torch.LongTensor([state["current_floor"]])

        # 各分量编码
        card_enc = self.card_embed(card_ids).mean(dim=0)  # [50,32] -> [32]
        potion_enc = self.potion_embed(potion_ids).flatten()  # [5,8] -> [40]
        path_enc = self.node_embed(path_nodes).flatten()  # [3,6,6] -> [108]
        boss_enc = self.boss_embed(boss_id).squeeze(0)  # [16]

        # 拼接所有特征
        return torch.cat([
            collection,
            card_enc,
            potion_enc,
            path_enc,
            current_links,
            next_links.flatten(),
            boss_enc,
            current_health,
            max_health,
            current_floor
        ])  # 总维度: 150+32+40+108+6+72+16+1+1+1 = 427


class DQN(torch.nn.Module):
    """定制Q网络"""
    def __init__(self, action_dim):
        super().__init__()
        self.encoder = StateEncoder()

        self.net = torch.nn.Sequential(
            torch.nn.Linear(427, CONFIG["hidden_dim"]),
            torch.nn.ReLU(),
            torch.nn.Linear(CONFIG["hidden_dim"], CONFIG["hidden_dim"]),
            torch.nn.ReLU(),
            torch.nn.Linear(CONFIG["hidden_dim"], action_dim)
        )

    def forward(self, state):
        encoded = self.encoder(state)
        return self.net(encoded)


class ReplayBuffer:
    def __init__(self, buffer_size):
        self.buffer = deque(maxlen=buffer_size)

    def add(self, state, action, reward, next_state, done):
        self.buffer.append((state, action, reward, next_state, done))

    def sample(self, batch_size):
        batch = random.sample(self.buffer, batch_size)
        states, actions, rewards, next_states, dones = zip(*batch)
        return states, actions, rewards, next_states, dones

    def __len__(self):
        return len(self.buffer)


def train_dqn(model, target_model, optimizer, replay_buffer, batch_size, gamma):
    if len(replay_buffer) < batch_size:
        return

    states, actions, rewards, next_states, dones = replay_buffer.sample(batch_size)

    states = [torch.LongTensor(state) for state in states]
    actions = torch.LongTensor(actions).unsqueeze(1)
    rewards = torch.LongTensor(rewards).unsqueeze(1)
    next_states = [torch.LongTensor(next_state) for next_state in next_states]
    dones = torch.LongTensor(dones).unsqueeze(1)

    # 计算当前Q值
    q_values = torch.cat([model(state).unsqueeze(0) for state in states], dim=0)
    current_q_values = q_values.gather(1, actions)

    # 计算目标Q值
    next_q_values = torch.cat([target_model(next_state).unsqueeze(0) for next_state in next_states], dim=0)
    max_next_q_values = next_q_values.max(1)[0].unsqueeze(1)
    target_q_values = rewards + (1 - dones) * gamma * max_next_q_values

    # 计算损失
    loss = nn.MSELoss()(current_q_values, target_q_values)

    # 反向传播和优化
    optimizer.zero_grad()
    loss.backward()
    optimizer.step()

    return loss.item()


def get_reward(state):
    collection = torch.LongTensor(state["collection"])
    potion_ids = torch.LongTensor(state["potion"])
    current_health = torch.LongTensor([state["current_health"]])
    max_health = torch.LongTensor([state["max_health"]])
    current_floor = torch.LongTensor([state["current_floor"]])

    # 计算 collection 中非零元素的数量
    collection_count = torch.count_nonzero(collection).item()
    # 计算药水数量
    potion_count = torch.count_nonzero(potion_ids).item()

    # 计算奖励
    reward = collection_count * 5 + potion_count * 1 + current_health.item() * 2 + max_health.item() * 0.5 + current_floor.item() * 10
    return reward


def get_state(context):
    main_activity = get_main_activity(context)
    gameCharacter = main_activity.getGameCharacter()
    collection = [0] * 150
    for relic in gameCharacter.relic_list:
        collection[relic] = 1

    card_deck = [0] * 50
    for card in gameCharacter.card_list:
        card_deck[card] = 1

    potion = [0] * 5
    for i, _ in enumerate(gameCharacter.potion_list[:5]):
        potion[i] = 1

    path_nodes = [[0] * 6 for _ in range(3)]
    current_links = [0, 0, 0, 0, 0, 0]
    next_links = [[[0] * 6 for _ in range(6)] for _ in range(2)]

    all_nodes = gameCharacter.currentMap.getNodes()
    for nodes in all_nodes:
        path_nodes[nodes.row][nodes.col] = node_dict[nodes.nodeType]

    state = {
        "collection": collection,
        "card_deck": card_deck,
        "potion": potion,
        "path_nodes": path_nodes,
        "current_links": current_links,
        "next_links": next_links,
        "boss_id": gameCharacter.floorBoss,
        "current_health": gameCharacter.current_health,
        "max_health": gameCharacter.max_health,
        "current_floor": gameCharacter.current_floor
    }
    return state

def push():
    return act

def main(context):
    action_dim = 6  # 假设最大6个可选节点
    model = DQN(action_dim)
    target_model = DQN(action_dim)
    target_model.load_state_dict(model.state_dict())
    target_model.eval()

    optimizer = optim.Adam(model.parameters(), lr=CONFIG["lr"])
    replay_buffer = ReplayBuffer(CONFIG["buffer_size"])

    epsilon = CONFIG["epsilon_start"]
    num_episodes = 1000  # 训练的回合数
    for episode in range(num_episodes):
        # 初始化环境，这里需要根据实际情况实现
        state = get_state(context)
        total_reward = 0
        done = state["current_floor"] >= 48
        while not done:
            # epsilon-greedy策略选择动作
            if random.random() < epsilon:
                action = random.randint(0, action_dim - 1)
            else:
                with torch.no_grad():
                    q_values = model(state)
                    action = torch.argmax(q_values).item()
                    act = action

            # 执行动作并获取奖励和下一个状态，这里需要根据实际情况实现
            next_state = get_state(context)  # 示例，需要替换为实际逻辑
            reward = get_reward()
            done = state["current_floor"] >= 48

            replay_buffer.add(state, action, reward, next_state, done)
            state = next_state
            total_reward += reward

            # 训练模型
            loss = train_dqn(model, target_model, optimizer, replay_buffer, CONFIG["batch_size"], CONFIG["gamma"])

        # 更新目标网络
        if episode % CONFIG["target_update"] == 0:
            target_model.load_state_dict(model.state_dict())

        # 衰减epsilon
        epsilon = max(epsilon * CONFIG["epsilon_decay"], CONFIG["epsilon_end"])

        print(f"Episode {episode}: Total Reward = {total_reward}, Loss = {loss if loss else 0}, Epsilon = {epsilon}")

    # 保存训练好的模型
    torch.save(model.state_dict(), "dqn_model.pth")


if __name__ == "__main__":
    main()

