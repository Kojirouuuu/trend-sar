import numpy as np
import networkx as nx
from dataclasses import dataclass
from enum import Enum, auto
from typing import List, Optional, Tuple
import random
import math
import os
import multiprocessing as mp
import sys
import time
from datetime import datetime
import logging

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

def _assert_consistency(network, state, infected_neighbors_num):
    # 非負 & 一致チェック（遅いので必要に応じてガード）
    if (infected_neighbors_num < 0).any():
        raise RuntimeError("infected_neighbors_num has negative entries")
    # spot check（全チェックが重ければサンプルでも可）
    k2 = _recount_infected_neighbors(network, state)
    if not np.array_equal(k2, infected_neighbors_num):
        raise RuntimeError("infected_neighbors_num inconsistent with adjacency")

def simulate_continuous_with_events(
    network,
    lam: float,              # λ: per I->S edge
    gamma: float,            # γ
    rho0: float,             # 初期感染率
    tmax: float,             # 終了時刻
    c: float,                # 回復レート: γ/(1 + c*k_inf)
    rng: Optional[random.Random] = None,
    steady_state_threshold: float = 0.001,  # 定常状態判定の閾値
    steady_state_time: float = 5.0,         # 定常状態判定の時間窓
) -> Tuple[np.ndarray, np.ndarray]:

    rng = rng or random.Random()

    I: List[int] = []
    times: List[float] = []

    infected_neighbors_num = np.zeros(network.N, dtype=int)
    state = np.zeros(network.N, dtype=int)
    is_i_to_s = np.zeros_like(network.edge_list, dtype=bool)  # 各「有向」I->S 辺フラグ

    # --- 初期化 ---
    initial_infected_num = int(network.N * rho0)
    if rho0 == 0.0:
        initial_infected_num = 0
    elif initial_infected_num == 0 and rho0 > 0.0:
        initial_infected_num = 1

    node_indices = list(range(network.N))
    rng.shuffle(node_indices)
    initially_infected = node_indices[:initial_infected_num]

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
    
    # 定常状態検出用の変数
    last_check_time = 0.0
    last_infected_ratio = current_infected / network.N
    steady_state_start_time = None

    while current_time < tmax and current_infected > 0:
        # --- 定常状態チェック ---
        # 感染が一度広がって以降は、定常状態でイベントが起こりすぎてしまう可能性がある。
        if current_infected >= network.N * 0.8:
            if current_time - last_check_time >= 0.1:  # 0.1秒ごとにチェック
                current_infected_ratio = current_infected / network.N
                ratio_change = abs(current_infected_ratio - last_infected_ratio)
                
                if ratio_change < steady_state_threshold:
                    if steady_state_start_time is None:
                        steady_state_start_time = current_time
                    elif current_time - steady_state_start_time >= steady_state_time:
                        # 定常状態が続いているので終了
                        break
                else:
                    # 変化がある場合は定常状態開始時刻をリセット
                    steady_state_start_time = None
                
                last_check_time = current_time
                last_infected_ratio = current_infected_ratio

        # --- レート計算 ---
        # 感染は各 True 辺ごとに lam
        K = int(is_i_to_s.sum())
        total_infect = lam * K

        # 回復は I ノードごとに γ/(1 + c*k_inf)
        den = 1.0 + c * infected_neighbors_num  # 理論上 >= 1
        # もし 0 以下があれば状態が破綻しているので再計算して防御
        if (den <= 0).any():
            infected_neighbors_num = _recount_infected_neighbors(network, state)
            den = 1.0 + c * infected_neighbors_num
            if (den <= 0).any():
                raise RuntimeError("denominator <= 0 after recount; c or counters are invalid")

        recover_rates = state * (gamma / den)
        total_recover = float(recover_rates.sum())

        total_rate = float(total_infect + total_recover)
        if not (total_rate > 0.0 and np.isfinite(total_rate)):
            break

        # --- 次イベント時刻 ---
        u = rng.random()
        dt = -math.log(u) / total_rate
        current_time += dt

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

        # --- デバッグ健全性（必要なときだけ） ---
        # _assert_consistency(network, state, infected_neighbors_num)

    return np.array(I), np.array(times)


def simulate_wrapper(task):
    """並列処理用のラッパー関数"""
    c_idx, lamb_idx, itr_idx, network, lam, gamma, rho0, tmax, c, rng, steady_state_threshold, steady_state_time = task
    I, times = simulate_continuous_with_events(network, lam, gamma, rho0, tmax, c, rng, steady_state_threshold, steady_state_time)
    return (c_idx, lamb_idx, itr_idx, I, times)


if __name__ == "__main__":
    # ==== ロガー設定（ファイル＋コンソール） ====
    logger = logging.getLogger("sim")
    logger.setLevel(logging.INFO)
    fmt = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    fh = logging.FileHandler("simulation_progress.log", mode="w")
    fh.setFormatter(fmt)
    ch = logging.StreamHandler(sys.stdout)
    ch.setFormatter(fmt)
    logger.handlers = [fh, ch]  # 既存ハンドラ差し替え

    # ==== 既存のセットアップ ====
    N = 4000
    k_ave = 6
    itr = 200
    file_num = 20  # ファイル分割数
    G = nx.random_regular_graph(k_ave, N)
    network = nx_to_network(G)

    c_list = [0.0, 0.1, 0.5, 1.0]
    dlamb = 0.0025
    lamb_values = np.arange(0.0, 0.4 + dlamb, dlamb)

    gamma = 1.0
    rho0 = 1.0
    tmax = 50
    
    # 定常状態検出のパラメータ
    steady_state_threshold = 40 / N  # 感染率の変化がこの値以下なら定常状態とみなす
    steady_state_time = 1.0         # 定常状態が続く時間（秒）

    # ==== タスク列挙 ====
    base_seed = 0
    tasks = []
    for c_idx, c in enumerate(c_list):
        for lamb_idx, lamb in enumerate(lamb_values):
            for itr_idx in range(itr):
                # 関数に必要な引数を適切な順序で渡す
                # simulate_continuous_with_events(network, lam, gamma, rho0, tmax, c, rng, steady_state_threshold, steady_state_time)
                rng = random.Random(base_seed + itr_idx)  # 各イテレーションで異なるシード
                # インデックス情報も含めてタスクを作成
                tasks.append((c_idx, lamb_idx, itr_idx, network, lamb, gamma, rho0, tmax, c, rng, steady_state_threshold, steady_state_time))

    total_tasks = len(tasks)
    procs = int(os.cpu_count()) or 1
    chunksize = max(1, total_tasks // (procs * 4))

    # ==== 実行開始ログ＆タイマー ====
    run_started_at = datetime.now().isoformat(timespec="seconds")
    t0 = time.perf_counter()
    logger.info(f"Run started: tasks={total_tasks}, processes={procs}, chunksize={chunksize}")
    logger.info(f"Steady state detection: threshold={steady_state_threshold}, time_window={steady_state_time}s")
    logger.info(f"Results will be split into {file_num} files")

    # ==== 並列実行（進捗ログはメインで更新） ====
    ctx = mp.get_context("spawn")
    results = []
    LOG_EVERY = max(1, total_tasks // 20)  # 5%ごとにログ

    with ctx.Pool(
        processes=procs,
    ) as pool:
        for n, res in enumerate(pool.imap_unordered(simulate_wrapper, tasks, chunksize=chunksize), start=1):
            results.append(res)
            if (n % LOG_EVERY == 0) or (n == total_tasks):
                elapsed = time.perf_counter() - t0
                rate = n / elapsed if elapsed > 0 else float("inf")
                eta = (total_tasks - n) / rate if np.isfinite(rate) and rate > 0 else float("inf")
                logger.info(f"Progress: {n}/{total_tasks} ({n/total_tasks:.1%}) | elapsed={elapsed:.1f}s | ETA={eta:.1f}s")

    # ==== 終了タイム計測 ====
    total_runtime_sec = time.perf_counter() - t0
    run_finished_at = datetime.now().isoformat(timespec="seconds")
    logger.info(f"Run finished. Total time = {total_runtime_sec:.2f}s")

    # ==== 受け皿に格納 ====
    shape = (len(c_list), len(lamb_values), itr)
    I_all = np.empty(shape, dtype=object)
    times_all = np.empty(shape, dtype=object)
    for c_idx, lamb_idx, itr_idx, I, times in results:
        I_all[c_idx, lamb_idx, itr_idx] = I
        times_all[c_idx, lamb_idx, itr_idx] = times

    # ==== .npz 保存（file_num回に分割） ====
    itr_per_file = itr // file_num
    remainder = itr % file_num
    
    for file_idx in range(file_num):
        # 各ファイルに含めるitrの範囲を計算
        start_itr = file_idx * itr_per_file
        if file_idx < remainder:
            start_itr += file_idx
        else:
            start_itr += remainder
            
        end_itr = start_itr + itr_per_file
        if file_idx < remainder:
            end_itr += 1
            
        # ファイル名に連番を付ける（01, 02, ...）
        file_suffix = f"{file_idx:02d}"
        filename = f"simulation_results_detail_RRG_rho1_{file_suffix}.npz"
        
        # 該当するitrの範囲のデータを抽出
        I_file = I_all[:, :, start_itr:end_itr]
        times_file = times_all[:, :, start_itr:end_itr]
        
        np.savez_compressed(
            filename,
            I_all=I_file,
            times_all=times_file,
            c_list=np.array(c_list, dtype=float),
            lamb_values=lamb_values,
            N=N,
            k_ave=k_ave,
            itr=end_itr - start_itr,  # このファイルに含まれるitr数
            itr_start=start_itr,       # 開始itr番号
            itr_end=end_itr,           # 終了itr番号
            file_idx=file_idx,         # ファイル番号
            total_files=file_num,      # 総ファイル数
            dlamb=dlamb,
            gamma=gamma,
            rho0=rho0,
            tmax=tmax,
            # 定常状態検出パラメータ
            steady_state_threshold=np.array(steady_state_threshold, dtype=float),
            steady_state_time=np.array(steady_state_time, dtype=float),
            # 追加メタ
            run_started_at=np.array(run_started_at),
            run_finished_at=np.array(run_finished_at),
            total_runtime_sec=np.array(total_runtime_sec, dtype=float),
            processes=np.array(procs, dtype=int),
            chunksize=np.array(chunksize, dtype=int),
        )
        logger.info(f"Saved file {file_idx + 1}/{file_num}: {filename} (itr {start_itr}-{end_itr-1})")
    
    logger.info(f"All {file_num} files saved successfully")
    logger.info("Simulation completed and results saved to multiple npz files")

