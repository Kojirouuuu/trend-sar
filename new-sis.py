import numpy as np
import networkx as nx
from typing import List, Optional, Tuple
import math
import os
import multiprocessing as mp
import sys
import time
from datetime import datetime
import logging

# ============================================================
# Network (CSR 形式) と変換
# ============================================================

class Network:
    """
    CSR 形式の無向グラフ（有向辺配列 edges は実質 2E）。
    - rowptr[i] ... ノード i の隣接開始位置
    - edges[rowptr[i]:rowptr[i+1]] ... 近傍ノード ID 群
    - src[k] ... 有向辺 k の始点ノード
    - rev[k] ... 有向辺 k の逆向き有向辺のインデックス（O(1) 参照）
    """
    __slots__ = ("N", "k_ave", "edges", "rowptr", "src", "rev")

    def __init__(self, N: int, k_ave: float,
                 edges: np.ndarray, rowptr: np.ndarray,
                 src: np.ndarray, rev: np.ndarray):
        self.N = N
        self.k_ave = k_ave
        self.edges = edges              # int32, shape=(2E,)
        self.rowptr = rowptr            # int32, shape=(N+1,)
        self.src = src                  # int32, shape=(2E,)
        self.rev = rev                  # int32, shape=(2E,)

    @property
    def M(self) -> int:
        """有向辺数 (= 2E)"""
        return self.edges.shape[0]

    def neighbors_slice(self, u: int) -> slice:
        return slice(self.rowptr[u], self.rowptr[u+1])

    def get_neighbors(self, u: int) -> np.ndarray:
        s = self.neighbors_slice(u)
        return self.edges[s]


def nx_to_network(G: nx.Graph) -> Network:
    # ノードは 0..N-1 を仮定（NetworkX の RRG はそう）
    N = G.number_of_nodes()
    k_ave = 2 * G.number_of_edges() / N
    print(f"N = {N}, k_ave = {k_ave}")

    # 度列と rowptr
    degs = np.fromiter((G.degree(i) for i in range(N)), dtype=np.int32, count=N)
    rowptr = np.empty(N + 1, dtype=np.int32)
    rowptr[0] = 0
    np.cumsum(degs, out=rowptr[1:])

    M = int(rowptr[-1])  # 有向辺数(=E*2)ではなく、ここは片側 E。以下で 2E を作る方が自然だが
    # NetworkX の無向グラフで neighbors をそのまま詰めると 2E が入る（片側で全近傍）
    # -> rowptr[-1] は実質 2E になる（各 i に deg(i) を足した総和）
    # よって M は有向辺数で正しい

    edges = np.empty(M, dtype=np.int32)
    src = np.empty(M, dtype=np.int32)
    # 1 パス目：edges と src を詰める & (u,v)->idx マップ作成
    uv2idx = {}
    for u in range(N):
        start = rowptr[u]
        nbrs = list(G.neighbors(u))
        edges[start:start+len(nbrs)] = nbrs
        src[start:start+len(nbrs)] = u
        for off, v in enumerate(nbrs):
            uv2idx[(u, v)] = start + off

    # 2 パス目：rev を作成
    rev = np.empty(M, dtype=np.int32)
    miss = 0
    for idx in range(M):
        u = src[idx]
        v = edges[idx]
        ridx = uv2idx.get((v, u), -1)
        if ridx < 0:
            miss += 1
        rev[idx] = ridx
    if miss:
        raise RuntimeError("Reverse edge construction failed; graph must be simple undirected.")

    return Network(N, float(k_ave), edges, rowptr, src, rev)


# ============================================================
# Fenwick Tree (BIT) for weighted sampling of recoveries
# ============================================================

class Fenwick:
    """
    0-indexed API の Fenwick 木（内部は 1-indexed）
    値は float64。加算更新 add(i, delta)、prefix 和、find_by_cumsum(x) を提供。
    """
    __slots__ = ("n", "tree")

    def __init__(self, n: int):
        self.n = int(n)
        self.tree = np.zeros(self.n + 1, dtype=np.float64)

    def build(self, arr: np.ndarray):
        # arr: shape (n,)
        self.tree[:] = 0.0
        for i, v in enumerate(arr, start=1):
            self.tree[i] += v
            j = i + (i & -i)
            if j <= self.n:
                self.tree[j] += self.tree[i]

    def add(self, i: int, delta: float):
        i += 1
        n = self.n
        tree = self.tree
        while i <= n:
            tree[i] += delta
            i += i & -i

    def prefix_sum(self, i: int) -> float:
        # sum of [0..i]
        i += 1
        s = 0.0
        tree = self.tree
        while i > 0:
            s += tree[i]
            i -= i & -i
        return s

    def total(self) -> float:
        # 全体和
        return self.prefix_sum(self.n - 1)

    def find_by_cumsum(self, target: float) -> int:
        """
        最小の idx で prefix_sum(idx) >= target を返す。
        target は [0, total) を仮定。
        """
        i = 0
        bit = 1 << (self.n.bit_length())
        tree = self.tree
        while bit:
            j = i + bit
            if j <= self.n and target >= tree[j]:
                target -= tree[j]
                i = j
            bit >>= 1
        # i は 1-indexed の直前位置
        idx = min(i, self.n - 1)
        return idx


# ============================================================
# シミュレーション（最適化版）
# ============================================================

def simulate_continuous_with_events(
    network: Network,
    lam: float,              # λ: per I->S edge
    gamma: float,            # γ
    rho0: float,             # 初期感染率
    tmax: float,             # 終了時刻
    c: float,                # 回復レート: γ/(1 + c*k_inf)
    seed: Optional[int] = None,
    steady_state_threshold: float = 0.001,
    steady_state_time: float = 5.0,
) -> Tuple[np.ndarray, np.ndarray]:

    rng = np.random.default_rng(seed)

    N = network.N
    edges = network.edges
    rowptr = network.rowptr
    src = network.src
    rev = network.rev
    M = network.M

    # 状態とカウンタ
    state = np.zeros(N, dtype=np.int8)             # 0=S, 1=I
    k_inf = np.zeros(N, dtype=np.int32)            # 各ノードの感染近傍数
    recover_rate = np.zeros(N, dtype=np.float64)   # γ/(1 + c*k_inf) for I, else 0

    # 有効 I->S 辺の管理（動的配列＋位置表）
    active_mask = np.zeros(M, dtype=bool)
    active_pos = np.full(M, -1, dtype=np.int64)
    active = []  # list[int]

    def activate(eidx: int):
        if not active_mask[eidx]:
            active_mask[eidx] = True
            active_pos[eidx] = len(active)
            active.append(eidx)

    def deactivate(eidx: int):
        if not active_mask[eidx]:
            return
        pos = active_pos[eidx]
        last_idx = active[-1]
        active[pos] = last_idx
        active_pos[last_idx] = pos
        active.pop()
        active_pos[eidx] = -1
        active_mask[eidx] = False

    # Fenwick 木で回復重みを管理
    bit = Fenwick(N)

    # 初期感染
    indices = np.arange(N)
    rng.shuffle(indices)
    init_I = int(round(N * rho0))
    if rho0 == 0.0:
        init_I = 0
    elif init_I == 0 and rho0 > 0.0:
        init_I = 1

    init_nodes = indices[:init_I]
    state[init_nodes] = 1

    # 初期 k_inf と I->S 辺活性化
    for u in init_nodes:
        s = rowptr[u]; t = rowptr[u+1]
        nbr_idx = range(s, t)
        for eidx in nbr_idx:
            v = edges[eidx]
            k_inf[v] += 1  # u が I なので v の感染近傍数を+1
    # I->S 辺の活性化（I から S へ向かう有向辺）
    for u in init_nodes:
        s = rowptr[u]; t = rowptr[u+1]
        for eidx in range(s, t):
            v = edges[eidx]
            if state[v] == 0:
                activate(eidx)

    # 回復レート初期化
    for u in init_nodes:
        recover_rate[u] = gamma / (1.0 + c * k_inf[u])

    bit.build(recover_rate)

    # 記録
    I_series: List[int] = [int(init_I)]
    T_series: List[float] = [0.0]

    current_time = 0.0
    current_infected = int(init_I)

    # 定常状態検出
    last_check_time = 0.0
    last_infected_ratio = current_infected / N if N > 0 else 0.0
    steady_state_start_time = None

    # ループ
    while current_time < tmax and (current_infected > 0 or len(active) > 0):
        # ---- 定常状態チェック（高感染率時のみ） ----
        # if current_infected >= 0.8 * N:
        #     if current_time - last_check_time >= 0.1:
        #         rnow = current_infected / N
        #         if abs(rnow - last_infected_ratio) < steady_state_threshold:
        #             if steady_state_start_time is None:
        #                 steady_state_start_time = current_time
        #             elif current_time - steady_state_start_time >= steady_state_time:
        #                 break
        #         else:
        #             steady_state_start_time = None
        #         last_check_time = current_time
        #         last_infected_ratio = rnow

        K = len(active)
        total_infect = lam * K
        total_recover = bit.total()
        total_rate = total_infect + total_recover
        if not (total_rate > 0.0 and np.isfinite(total_rate)):
            break

        # 次イベント時刻
        u = rng.random()
        dt = -math.log(u) / total_rate
        current_time += dt

        r = rng.random() * total_rate

        if r < total_infect and K > 0:
            # --------- 感染イベント：I->S 辺を一様サンプリング ---------
            pick = rng.integers(K)
            eidx = active[pick]
            i = src[eidx]
            j = edges[eidx]  # i -> j (j は S のはず)
            # 適用
            # 1) 使用した I->S 辺を無効化
            deactivate(eidx)
            # 2) j を感染させる
            state[j] = 1
            current_infected += 1
            # 3) 近傍更新 & I->S 辺の切替／回復率差分更新
            s = rowptr[j]; t = rowptr[j+1]
            # j 自身の回復レート（k_inf[j] は「近傍に I が何人いるか」なので変化しない）
            new_r_j = gamma / (1.0 + c * k_inf[j]) if gamma > 0 else 0.0
            if recover_rate[j] != new_r_j:
                bit.add(j, new_r_j - recover_rate[j])
                recover_rate[j] = new_r_j

            for ejn in range(s, t):
                n = edges[ejn]
                # n の感染近傍数を +1
                k_inf[n] += 1
                if state[n] == 0:
                    # j->n は I->S になるので活性化
                    activate(ejn)
                else:
                    # n が I：n->j は I->I なので無効化
                    deactivate(rev[ejn])
                    # n の回復レートは分母が増えて減少
                    old = recover_rate[n]
                    new = gamma / (1.0 + c * k_inf[n]) if gamma > 0 else 0.0
                    if old != new:
                        bit.add(n, new - old)
                        recover_rate[n] = new

            I_series.append(current_infected)
            T_series.append(current_time)

        else:
            # --------- 回復イベント：重み付き（Fenwick 木）サンプリング ---------
            if total_recover <= 0.0:
                # 感染イベントしかない（K>0）場合はスキップ
                continue
            r2 = r - total_infect
            target = r2  # [0, total_recover)
            j = bit.find_by_cumsum(target)
            if state[j] == 0 or recover_rate[j] <= 0.0:
                # 数値誤差や端点ヒット時の頑健性: 線形探索で補正
                #（ヒット件数は非常に少ないため支障なし）
                jj = j
                found = False
                for _ in range(network.N):
                    if state[jj] == 1 and recover_rate[jj] > 0.0:
                        j = jj
                        found = True
                        break
                    jj = (jj + 1) % network.N
                if not found:
                    # 回復イベントが存在しない
                    continue

            # 反映：j を回復( I->S )
            # 1) j の回復レートを 0 に
            if recover_rate[j] > 0.0:
                bit.add(j, -recover_rate[j])
                recover_rate[j] = 0.0

            # 2) 近傍更新 & I→S 辺切替
            s = rowptr[j]; t = rowptr[j+1]
            for ejn in range(s, t):
                n = edges[ejn]
                # j->n は j が S になるため必ず無効化
                deactivate(ejn)
                # 近傍 n の感染近傍数を -1
                k_inf[n] -= 1
                if state[n] == 1:
                    # n->j は I->S になるので活性化
                    activate(rev[ejn])
                    # n の回復レートは分母が減って増加
                    old = recover_rate[n]
                    new = gamma / (1.0 + c * k_inf[n]) if gamma > 0 else 0.0
                    if old != new:
                        bit.add(n, new - old)
                        recover_rate[n] = new

            state[j] = 0
            current_infected -= 1

            I_series.append(current_infected)
            T_series.append(current_time)

    return np.asarray(I_series, dtype=np.int32), np.asarray(T_series, dtype=np.float64)


# ============================================================
# 並列実行ラッパ & 初期化
# ============================================================

_GLOBAL_NETWORK: Optional[Network] = None
def _init_worker(network: Network):
    global _GLOBAL_NETWORK
    _GLOBAL_NETWORK = network

def simulate_wrapper(task):
    """
    並列処理用ラッパ：ネットワークはグローバルから参照
    task = (c_idx, lamb_idx, itr_idx, lam, gamma, rho0, tmax, c, seed, ssth, sstime)
    """
    (c_idx, lamb_idx, itr_idx, lam, gamma, rho0, tmax, c,
     seed, steady_state_threshold, steady_state_time) = task
    I, times = simulate_continuous_with_events(
        _GLOBAL_NETWORK, lam, gamma, rho0, tmax, c, seed,
        steady_state_threshold, steady_state_time
    )
    return (c_idx, lamb_idx, itr_idx, I, times)


# ============================================================
# 進捗の細粒度ログ付きメイン
# ============================================================

def _itr_slices(total_itr: int, file_num: int) -> List[Tuple[int, int]]:
    """ファイル分割用に [start, end) の itr 範囲を返す（できるだけ均等に）"""
    base = total_itr // file_num
    rem = total_itr % file_num
    slices = []
    start = 0
    for i in range(file_num):
        extra = 1 if i < rem else 0
        end = start + base + extra
        slices.append((start, end))
        start = end
    return slices


if __name__ == "__main__":
    # ==== ロガー設定（ファイル＋コンソール） ====
    logger = logging.getLogger("sim")
    logger.setLevel(logging.INFO)
    fmt = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    fh = logging.FileHandler("simulation_progress.log", mode="w", encoding="utf-8")
    fh.setFormatter(fmt)
    ch = logging.StreamHandler(sys.stdout)
    ch.setFormatter(fmt)
    logger.handlers = [fh, ch]  # 差し替え

    # ==== パラメータ ====
    N = 4000
    k_ave = 16
    itr = 200
    file_num = 20  # ファイル分割数
    G = nx.random_regular_graph(k_ave, N)
    network = nx_to_network(G)

    c_list = [0.0, 0.7, 1.8]
    dlamb = 0.005
    lamb_values = np.arange(0.0, 0.3 + dlamb, dlamb)

    gamma = 1.0
    rho0 = 1.0
    tmax = 50.0

    # 定常状態検出のパラメータ
    steady_state_threshold = 60 / N
    steady_state_time = 3.0

    base_seed = 0

    total_tasks_all = len(c_list) * len(lamb_values) * itr
    procs = int(os.cpu_count() or 1)
    run_started_at = datetime.now().isoformat(timespec="seconds")
    t0_all = time.perf_counter()

    logger.info(f"Run started: tasks={total_tasks_all}, processes={procs}")
    logger.info(f"Steady state: threshold={steady_state_threshold}, window={steady_state_time}s")
    logger.info(f"Results will be split into {file_num} files")

    # ==== プロセスプールを先に立てる（ネットワークを一度だけ配布） ====
    ctx = mp.get_context("spawn")
    pool = ctx.Pool(processes=procs, initializer=_init_worker, initargs=(network,))

    try:
        # 全体カウンタ
        done_all = 0
        last_log_t_global = time.perf_counter()
        rate_ema = None  # スループットの指数移動平均

        for file_idx, (itr_s, itr_e) in enumerate(_itr_slices(itr, file_num)):
            # ==== このファイルのタスク ====
            tasks = []
            for c_idx, c in enumerate(c_list):
                for lamb_idx, lamb in enumerate(lamb_values):
                    for itr_idx in range(itr_s, itr_e):
                        seed = base_seed + itr_idx  # 反復で異なるシード
                        tasks.append((c_idx, lamb_idx, itr_idx, lamb, gamma, rho0,
                                      tmax, c, seed, steady_state_threshold, steady_state_time))

            total_tasks_batch = len(tasks)
            t0_batch = time.perf_counter()
            done_batch = 0
            last_log_t_batch = t0_batch

            # 受け皿（このバッチ分のみを保持）
            shape = (len(c_list), len(lamb_values), itr_e - itr_s)
            I_file = np.empty(shape, dtype=object)
            T_file = np.empty(shape, dtype=object)

            # 動的 chunksize（経験則）
            chunksize = max(1, total_tasks_batch // (procs * 8))

            logger.info(f"[File {file_idx+1}/{file_num}] tasks={total_tasks_batch}, itr={itr_s}-{itr_e-1}, chunksize={chunksize}")

            for res in pool.imap_unordered(simulate_wrapper, tasks, chunksize=chunksize):
                c_idx, lamb_idx, itr_idx, I, times = res
                j = itr_idx - itr_s
                I_file[c_idx, lamb_idx, j] = I
                T_file[c_idx, lamb_idx, j] = times

                # 進捗更新（時間ベースで細かく）
                done_batch += 1
                done_all += 1
                now = time.perf_counter()

                # グローバルの移動平均スループット
                dtg = now - last_log_t_global
                if dtg >= 0.5:  # 0.5秒ごとに表示
                    inst_rate = (done_all) / max(now - t0_all, 1e-9)
                    if rate_ema is None:
                        rate_ema = inst_rate
                    else:
                        rate_ema = 0.2 * inst_rate + 0.8 * rate_ema
                    eta_all = (total_tasks_all - done_all) / max(rate_ema, 1e-9)
                    logger.info(f"Overall: {done_all}/{total_tasks_all} ({done_all/total_tasks_all:.1%}) "
                                f"| rate(avg)={rate_ema:.1f} it/s | ETA={eta_all:.1f}s")
                    last_log_t_global = now

                dtb = now - last_log_t_batch
                if dtb >= 0.5:
                    rate = done_batch / max(now - t0_batch, 1e-9)
                    eta = (total_tasks_batch - done_batch) / max(rate, 1e-9)
                    logger.info(f"  [File {file_idx+1}/{file_num}] {done_batch}/{total_tasks_batch} "
                                f"({done_batch/total_tasks_batch:.1%}) | elapsed={now - t0_batch:.1f}s | ETA={eta:.1f}s")
                    last_log_t_batch = now

            # ==== バッチ保存 ====
            run_finished_at = datetime.now().isoformat(timespec="seconds")
            total_runtime_sec_batch = time.perf_counter() - t0_batch
            filename = f"simulation_results_detail_RRG_rho1_N={N}_{file_idx:02d}.npz"

            np.savez_compressed(
                filename,
                I_all=I_file,
                times_all=T_file,
                c_list=np.array(c_list, dtype=float),
                lamb_values=lamb_values,
                N=N,
                k_ave=k_ave,
                itr=(itr_e - itr_s),
                itr_start=itr_s,
                itr_end=itr_e,
                file_idx=file_idx,
                total_files=file_num,
                dlamb=dlamb,
                gamma=gamma,
                rho0=rho0,
                tmax=tmax,
                steady_state_threshold=np.array(steady_state_threshold, dtype=float),
                steady_state_time=np.array(steady_state_time, dtype=float),
                run_started_at=np.array(run_started_at),
                run_finished_at=np.array(run_finished_at),
                total_runtime_sec=np.array(total_runtime_sec_batch, dtype=float),
                processes=np.array(procs, dtype=int),
                chunksize=np.array(chunksize, dtype=int),
            )
            logger.info(f"[File {file_idx+1}/{file_num}] saved: {filename} (itr {itr_s}-{itr_e-1})")

        total_runtime_sec_all = time.perf_counter() - t0_all
        logger.info(f"Run finished. Total time = {total_runtime_sec_all:.2f}s")

    finally:
        pool.close()
        pool.join()
        logger.info("Pool closed.")
