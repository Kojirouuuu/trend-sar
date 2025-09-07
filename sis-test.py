import numpy as np
import networkx as nx
from dataclasses import dataclass
from enum import Enum, auto
from typing import List, Optional, Tuple
import random
import math
import os

# --- 返り値・イベント定義 ----------------------------------------------------

class Network:
    N: int
    k_ave: float
    edge_list: List[int]
    address_list: List[int]
    cursor_list: List[int]

    def __init__(self, N, k_ave, edge_list, address_list, cursor_list):
        self.N = N
        self.k_ave = k_ave
        self.edge_list = edge_list
        self.address_list = address_list
        self.cursor_list = cursor_list

    def get_neighbors(self, node_index: int) -> List[int]:
        return self.edge_list[self.address_list[node_index]:self.cursor_list[node_index]]

def nx_to_network(G: nx.Graph) -> Network:
    N = G.number_of_nodes()
    k_ave = 2 * G.number_of_edges() / N
    print(f"N = {N}, k_ave = {k_ave}")

    address_list = np.zeros(N, dtype=int)
    cursor_list = np.zeros(N, dtype=int)
    for i in range(N-1):
        deg = len(list(G.neighbors(i)))
        address_list[i + 1] = address_list[i] + deg
        cursor_list[i] = address_list[i + 1]

    cursor_list[N-1] = cursor_list[N-2] + len(list(G.neighbors(N-1)))

    edge_list = np.zeros(2 * G.number_of_edges(), dtype=int)

    for i in range(N):
        for jidx, j in enumerate(list(G.neighbors(i))):
            edge_list[address_list[i] + jidx] = j

    return Network(N, k_ave, edge_list, address_list, cursor_list)


# --- メイン：連続時間 SIS シミュレーション（イベント記録付き） -------------

def _recount_infected_neighbors(network, state: np.ndarray) -> np.ndarray:
    """安全用: k_inf[u] = Σ_{v∈N(u)} state[v] を再計算"""
    k_inf = np.zeros(network.N, dtype=int)
    for u in range(network.N):
        s = 0
        for v in network.get_neighbors(u):
            s += state[v]
        k_inf[u] = s
    return k_inf

def simulate_continuous_with_events(
    network,
    lam: float,              # λ: per I->S edge
    gamma: float,            # γ
    rho0: float,             # 初期感染率
    tmax: float,             # 終了時刻
    c: float,                # 回復レート: γ/(1 + c*k_inf)
    rng: Optional[random.Random] = None
) -> Tuple[np.ndarray, np.ndarray]:

    rng = rng or random.Random()

    # 感染者数の時系列
    I: List[int] = []
    times: List[float] = []

    # 各ノードの感染近傍数
    infected_neighbors_num = np.zeros(network.N, dtype=int)
    state = np.zeros(network.N, dtype=int)

    # その辺はI->Sであるか
    is_i_to_s = np.zeros_like(network.edge_list, dtype=bool)

    # --- 初期化 ---
    initial_infected_num = int(network.N * rho0)
    if rho0 == 0.0:
        initial_infected_num = 0
    elif initial_infected_num == 0 and rho0 > 0.0:
        initial_infected_num = 1

    # 初期感染者をシャッフル
    node_indices = list(range(network.N))
    rng.shuffle(node_indices)
    initially_infected = node_indices[:initial_infected_num]

    # 初期感染者を設定
    for u in initially_infected:
        state[u] = 1
    for u in initially_infected:
        for n in network.get_neighbors(u):
            infected_neighbors_num[n] += 1
    # すべての I->S 辺を True に（最初の1本だけ、ではなく全部）
    for u in initially_infected:
        base = network.address_list[u]
        deg_u = network.cursor_list[u] - base
        for j_idx in range(deg_u):
            v = network.edge_list[base + j_idx]
            if state[v] == 0:
                is_i_to_s[base + j_idx] = True

    I.append(initial_infected_num)
    times.append(0.0)

    current_time = 0.0
    current_infected = initial_infected_num

    while current_time < tmax and current_infected > 0:

        # --- レート計算 ---
        # 感染は各 True 辺ごとに lam
        K = int(is_i_to_s.sum())
        total_infect = lam * K

        # 回復は I ノードごとに γ/(1 + c*k_inf)
        den = 1.0 + c * infected_neighbors_num  # 理論上 >= 1
        # もし 0 以下があれば状態が破綻しているので再計算して防御

        # susceptibleならばstate=0となり、回復率に加算されない。
        recover_rates = state * (gamma / den)
        total_recover = float(recover_rates.sum())

        total_rate = float(total_infect + total_recover)

        # --- 次イベント時刻 ---
        u = rng.random()
        dt = -math.log(u) / total_rate
        current_time += dt

        # イベント時刻がtmaxを超える場合、記録しない
        if current_time + dt > tmax:
            break

        # --- 種別抽選 ---
        r = rng.random() * total_rate

        if r < total_infect:
            # ================= 感染イベント =================
            r_edge = r
            fired = False
            for i in range(network.N):
                base = network.address_list[i]
                end = network.cursor_list[i]
                for idx in range(base, end):
                    # その辺はI->Sであるか
                    if is_i_to_s[idx]:
                        r_edge -= lam
                        if r_edge < 0:
                            j = network.edge_list[idx]  # i -> j (j は S)
                            # 反映
                            state[j] = 1
                            current_infected += 1
                            # 使用した i->j を明示的に無効化
                            is_i_to_s[idx] = False
                            # 近傍・辺フラグ更新
                            for n_off, n in enumerate(network.get_neighbors(j)):
                                infected_neighbors_num[n] += 1
                                # j は I になった：j->S を有効化、I へは無効
                                if state[n] == 0:
                                    is_i_to_s[network.address_list[j] + n_off] = True
                                else:
                                    # n が I：n->j は I->I なので無効化
                                    base_n = network.address_list[n]
                                    for nn_off, nn in enumerate(network.get_neighbors(n)):
                                        if nn == j:
                                            is_i_to_s[base_n + nn_off] = False
                                            break
                            I.append(current_infected)
                            times.append(current_time)
                            fired = True
                            break
                if fired:
                    break
            if not fired:
                # ここに来るのは浮動小数誤差等。安全にやめる
                break

        else:
            # ================= 回復イベント（ノード単位） =================
            r2 = r - total_infect
            fired = False
            for j in range(network.N):
                if state[j] == 1:
                    rate_j = gamma / (1.0 + c * infected_neighbors_num[j])
                    r2 -= rate_j
                    if r2 < 0:
                        # 反映
                        state[j] = 0
                        current_infected -= 1
                        base_j = network.address_list[j]
                        # j から見た全辺の I->S フラグは落とす（j は S）
                        for n_off, n in enumerate(network.get_neighbors(j)):
                            infected_neighbors_num[n] -= 1
                            is_i_to_s[base_j + n_off] = False
                            # n が I なら n->j は I->S になり有効化
                            if state[n] == 1:
                                base_n = network.address_list[n]
                                for nn_off, nn in enumerate(network.get_neighbors(n)):
                                    if nn == j:
                                        is_i_to_s[base_n + nn_off] = True
                                        break
                        I.append(current_infected)
                        times.append(current_time)
                        fired = True
                        break
            if not fired:
                break

    return np.array(I), np.array(times)


def run_single_simulation(args):
    """並列処理用：単一パラメータ組み合わせのシミュレーション実行"""
    network, lamb, gamma, rho0, tmax, c, itr_idx, seed = args
    
    # 各プロセスで独立したRNGを使用
    rng = random.Random(seed)
    
    I, times = simulate_continuous_with_events(network, lamb, gamma, rho0, tmax, c, rng)
    return itr_idx, I, times


if __name__ == "__main__":
    import multiprocessing as mp
    from functools import partial
    import pickle

    # ==== 既存のセットアップ ====
    network_type = "RR"
    N = 1000
    k_ave = 6
    itr = 100
    G = nx.random_regular_graph(k_ave, N)
    network = nx_to_network(G)

    lamb_list = [0.10, 0.12]
    dc = 0.05
    c_values = np.arange(0.5, 1.0 + dc, dc)

    gamma = 1.0
    rho0 = 1.0
    tmax = 60

    shape = (len(lamb_list), len(c_values), itr)
    I_all = np.empty(shape, dtype=object)
    times_all = np.empty(shape, dtype=object)

    # 並列処理の設定
    n_cores = mp.cpu_count()
    print(f"利用可能なコア数: {n_cores}")
    
    # プロセスプールを作成
    with mp.Pool(processes=n_cores) as pool:
        total_simulations = len(lamb_list) * len(c_values) * itr
        completed = 0
        
        for l_idx, lamb in enumerate(lamb_list):
            print(f"lamb: {l_idx + 1} / {len(lamb_list)}")
            for c_idx, c in enumerate(c_values):
                is_print_c = c_idx % 10 == 0 or c_idx == len(c_values) - 1
                if is_print_c:
                    print(f" -->c: {c_idx + 1} / {len(c_values)}")
                
                # このパラメータ組み合わせの全イテレーションを並列実行
                args_list = []
                for itr_idx in range(itr):
                    # 各プロセスで独立したシードを使用
                    seed = hash(f"{lamb}_{c}_{itr_idx}") % (2**32)
                    args = (network, lamb, gamma, rho0, tmax, c, itr_idx, seed)
                    args_list.append(args)
                
                # 並列実行
                results = pool.map(run_single_simulation, args_list)
                
                # 結果を格納
                for itr_idx, I, times in results:
                    I_all[l_idx, c_idx, itr_idx] = I
                    times_all[l_idx, c_idx, itr_idx] = times
                    completed += 1
                
                if is_print_c:
                    print(f"    完了: {completed}/{total_simulations} ({completed/total_simulations*100:.1f}%)")
    
    dirname = f"output/sis/{network_type}/z={k_ave}/N={N}time"
    if not os.path.exists(dirname):
        os.makedirs(dirname)

    filename = os.path.join(dirname, f"python.npz")
    np.savez_compressed(
        filename,
        I_all=I_all,
        times_all=times_all,
        c_values=np.array(c_values, dtype=float),
        lamb_list=lamb_list,
    )

