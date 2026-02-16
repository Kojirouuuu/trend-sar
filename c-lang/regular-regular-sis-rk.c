#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <errno.h>
#include <math.h>
#define Z 6
#define Z_BAR 6
#define N 10000
#define N_BAR 10000
#define n_r 2
#define dt 1e-4

FILE *fpdata;
// 初期採用者はグラフGのみから選ばれる。
// 内部=同一コミュニティ内の隣接、外部=他コミュニティへの隣接。s[q][m][n]: q=外部リンク数、m=内部隣接I数、n=外部隣接I数。
double s[Z+1][Z+1][Z+1],ds[Z+1][Z+1][Z+1];// G所属・次数k・外部q本・内部隣接I=m・外部隣接I=n のsとその変化量
double i[Z+1][Z+1][Z+1],di[Z+1][Z+1][Z+1];// G所属・次数k・外部q本・内部隣接I=m・外部隣接I=n のiとその変化量
double s_bar[Z_BAR+1][Z_BAR+1][Z_BAR+1],ds_bar[Z_BAR+1][Z_BAR+1][Z_BAR+1];// G_bar所属・次数k・外部q本・内部隣接I=m・外部隣接I=n のsとその変化量
double i_bar[Z_BAR+1][Z_BAR+1][Z_BAR+1],di_bar[Z_BAR+1][Z_BAR+1][Z_BAR+1];// G_bar所属・次数k・外部q本・内部隣接I=m・外部隣接I=n のiとその変化量
double S, I;
/* RK4 用の一時配列 */
static double s_old[Z+1][Z+1][Z+1], i_old[Z+1][Z+1][Z+1];
static double s_bar_old[Z_BAR+1][Z_BAR+1][Z_BAR+1], i_bar_old[Z_BAR+1][Z_BAR+1][Z_BAR+1];
static double k1_s[Z+1][Z+1][Z+1], k1_i[Z+1][Z+1][Z+1], k1_s_bar[Z_BAR+1][Z_BAR+1][Z_BAR+1], k1_i_bar[Z_BAR+1][Z_BAR+1][Z_BAR+1];
static double k2_s[Z+1][Z+1][Z+1], k2_i[Z+1][Z+1][Z+1], k2_s_bar[Z_BAR+1][Z_BAR+1][Z_BAR+1], k2_i_bar[Z_BAR+1][Z_BAR+1][Z_BAR+1];
static double k3_s[Z+1][Z+1][Z+1], k3_i[Z+1][Z+1][Z+1], k3_s_bar[Z_BAR+1][Z_BAR+1][Z_BAR+1], k3_i_bar[Z_BAR+1][Z_BAR+1][Z_BAR+1];

double* build_binom_pmf(int k, double p){
    if (k < 0) return NULL;
    if (p < 0.0 || p > 1.0) return NULL;

    double *pmf = calloc((size_t)k + 1, sizeof *pmf);
    if (!pmf) return NULL;

    if (p == 0.0) { pmf[0] = 1.0; return pmf; }
    if (p == 1.0) { pmf[k] = 1.0; return pmf; }

    const double q = 1.0 - p;

    // P(0) = q^k（kが小さいので pow でもOK）
    pmf[0] = pow(q, (double)k);

    // 漸化式: P(i) = P(i-1) * (k-i+1)/i * (p/q)
    const double ratio = p / q;
    for (int i = 1; i <= k; i++) {
        pmf[i] = pmf[i - 1] * (double)(k - i + 1) / (double)i * ratio;
    }

    // 誤差対策の正規化（k=10ならほぼ不要だが入れておくと堅い）
    double sum = 0.0;
    for (int i = 0; i <= k; i++) sum += pmf[i];
    if (sum > 0.0) {
        for (int i = 0; i <= k; i++) pmf[i] /= sum;
    }

    return pmf;
}

void init(int k,int k_bar,double rho){
    int q,l,m,n,q_bar,l_bar,m_bar,n_bar;
    double f;

    double eps=2.0*n_r/Z/N;
    double eps_bar=2.0*n_r/Z_BAR/N_BAR;

    double *binom_q=build_binom_pmf(k,eps);

    for(q=0;q<=k;q++){
        l=k-q;
        double *binom_m=build_binom_pmf(l,rho);

        for(m=0;m<=l;m++){
            f=binom_q[q]*binom_m[m];
            s[q][m][0]=(1.-rho)*f;i[q][m][0]=rho*f;
            for(n=1;n<=q;n++){
                s[q][m][n]=0.;
                i[q][m][n]=0.;
            }
        }
        free(binom_m);
    }
    double *binom_q_bar=build_binom_pmf(k_bar,eps_bar);

    for(q_bar=0;q_bar<=k_bar;q_bar++){
        l_bar=k_bar-q_bar;
        double *binom_n_bar=build_binom_pmf(q_bar,rho);
        
        for(m_bar=0;m_bar<=l_bar;m_bar++){
            f=binom_q_bar[q_bar];
            if(m_bar!=0){f=0.;}
            for(n_bar=0;n_bar<=q_bar;n_bar++){
                s_bar[q_bar][m_bar][n_bar]=f*binom_n_bar[n_bar];
                i_bar[q_bar][m_bar][n_bar]=0.;
            }
        }
        free(binom_n_bar);
    }
    free(binom_q_bar);
    free(binom_q);
}

static inline double gamma_hype(int infected_neighbors, double mu0, double c){
    return mu0 / (1.0 + c * (double)infected_neighbors);
}

/* 現在の s, i, s_bar, i_bar から ds, di, ds_bar, di_bar を計算（状態は更新しない） */
static void compute_derivatives(int k, int k_bar, double lambda, double mu0, double c)
{
    // ===== 1) コミュニティ1(G)の平均レート λ_in/out, γ_in/out を計算 =====
    double den_bin_S=0, num_bin_S=0;
    double den_bin_I=0, num_bin_I=0;
    double den_bout_S=0, num_bout_S=0;
    double den_bout_I=0, num_bout_I=0;

    double den_gin_S=0, num_gin_S=0;
    double den_gin_I=0, num_gin_I=0;
    double den_gout_S=0, num_gout_S=0;
    double den_gout_I=0, num_gout_I=0;

    for(int q=0; q<=k; q++){
        for(int m=0; m<=k-q; m++){
            for(int n=0; n<=q; n++){
                const double s_qmn = s[q][m][n];
                const double i_qmn = i[q][m][n];
                const int inf = m + n;
                const double gm = gamma_hype(inf, mu0, c);

                const int a_in_S = (k - q - m); // (z-q)-m = 内部S隣接数
                const int a_out_S = (q - n);    // q-n     = 外部S隣接数

                // λ in/out は s から
                den_bin_S += (double)a_in_S * s_qmn;
                num_bin_S += (double)a_in_S * (double)inf * s_qmn;

                den_bin_I += (double)m * s_qmn;
                num_bin_I += (double)m * (double)inf * s_qmn;

                den_bout_S += (double)a_out_S * s_qmn;
                num_bout_S += (double)a_out_S * (double)inf * s_qmn;

                den_bout_I += (double)n * s_qmn;
                num_bout_I += (double)n * (double)inf * s_qmn;

                // γ in/out は i から
                den_gin_S += (double)a_in_S * i_qmn;
                num_gin_S += (double)a_in_S * gm * i_qmn;

                den_gin_I += (double)m * i_qmn;
                num_gin_I += (double)m * gm * i_qmn;

                den_gout_S += (double)a_out_S * i_qmn;
                num_gout_S += (double)a_out_S * gm * i_qmn;

                den_gout_I += (double)n * i_qmn;
                num_gout_I += (double)n * gm * i_qmn;

                ds[q][m][n] = 0.0;
                di[q][m][n] = 0.0;
            }
        }
    }

    const double lambda_in_S  = (den_bin_S  > 0.0) ? lambda * (num_bin_S  / den_bin_S)  : 0.0;
    const double lambda_in_I  = (den_bin_I  > 0.0) ? lambda * (num_bin_I  / den_bin_I)  : 0.0;
    const double lambda_out_S = (den_bout_S > 0.0) ? lambda * (num_bout_S / den_bout_S) : 0.0;
    const double lambda_out_I = (den_bout_I > 0.0) ? lambda * (num_bout_I / den_bout_I) : 0.0;

    const double gamma_in_S  = (den_gin_S  > 0.0) ? (num_gin_S  / den_gin_S)  : 0.0;
    const double gamma_in_I  = (den_gin_I  > 0.0) ? (num_gin_I  / den_gin_I)  : 0.0;
    const double gamma_out_S = (den_gout_S > 0.0) ? (num_gout_S / den_gout_S) : 0.0;
    const double gamma_out_I = (den_gout_I > 0.0) ? (num_gout_I / den_gout_I) : 0.0;

    // ===== 2) コミュニティ2(Ḡ)の平均レート λ̄_in/out, γ̄_in/out を計算 =====
    double den_bbar_in_S=0, num_bbar_in_S=0;
    double den_bbar_in_I=0, num_bbar_in_I=0;
    double den_bbar_out_S=0, num_bbar_out_S=0;
    double den_bbar_out_I=0, num_bbar_out_I=0;

    double den_gbar_in_S=0, num_gbar_in_S=0;
    double den_gbar_in_I=0, num_gbar_in_I=0;
    double den_gbar_out_S=0, num_gbar_out_S=0;
    double den_gbar_out_I=0, num_gbar_out_I=0;

    for(int q=0; q<=k_bar; q++){
        for(int m=0; m<=k_bar-q; m++){
            for(int n=0; n<=q; n++){
                const double s_qmn = s_bar[q][m][n];
                const double i_qmn = i_bar[q][m][n];
                const int inf = m + n;
                const double gm = gamma_hype(inf, mu0, c);

                const int a_in_S = (k_bar - q - m);
                const int a_out_S = (q - n);

                den_bbar_in_S += (double)a_in_S * s_qmn;
                num_bbar_in_S += (double)a_in_S * (double)inf * s_qmn;

                den_bbar_in_I += (double)m * s_qmn;
                num_bbar_in_I += (double)m * (double)inf * s_qmn;

                den_bbar_out_S += (double)a_out_S * s_qmn;
                num_bbar_out_S += (double)a_out_S * (double)inf * s_qmn;

                den_bbar_out_I += (double)n * s_qmn;
                num_bbar_out_I += (double)n * (double)inf * s_qmn;

                den_gbar_in_S += (double)a_in_S * i_qmn;
                num_gbar_in_S += (double)a_in_S * gm * i_qmn;

                den_gbar_in_I += (double)m * i_qmn;
                num_gbar_in_I += (double)m * gm * i_qmn;

                den_gbar_out_S += (double)a_out_S * i_qmn;
                num_gbar_out_S += (double)a_out_S * gm * i_qmn;

                den_gbar_out_I += (double)n * i_qmn;
                num_gbar_out_I += (double)n * gm * i_qmn;

                ds_bar[q][m][n] = 0.0;
                di_bar[q][m][n] = 0.0;
            }
        }
    }

    const double lambda_bar_in_S  = (den_bbar_in_S  > 0.0) ? lambda * (num_bbar_in_S  / den_bbar_in_S)  : 0.0;
    const double lambda_bar_in_I  = (den_bbar_in_I  > 0.0) ? lambda * (num_bbar_in_I  / den_bbar_in_I)  : 0.0;
    const double lambda_bar_out_S = (den_bbar_out_S > 0.0) ? lambda * (num_bbar_out_S / den_bbar_out_S) : 0.0;
    const double lambda_bar_out_I = (den_bbar_out_I > 0.0) ? lambda * (num_bbar_out_I / den_bbar_out_I) : 0.0;

    const double gamma_bar_in_S  = (den_gbar_in_S  > 0.0) ? (num_gbar_in_S  / den_gbar_in_S)  : 0.0;
    const double gamma_bar_in_I  = (den_gbar_in_I  > 0.0) ? (num_gbar_in_I  / den_gbar_in_I)  : 0.0;
    const double gamma_bar_out_S = (den_gbar_out_S > 0.0) ? (num_gbar_out_S / den_gbar_out_S) : 0.0;
    const double gamma_bar_out_I = (den_gbar_out_I > 0.0) ? (num_gbar_out_I / den_gbar_out_I) : 0.0;

    // ===== 3) ODE (24)(25): コミュニティ1 更新（外部の増減は Ḡ 側レートを使う） =====
    for(int q=0; q<=k; q++){
        for(int m=0; m<=k-q; m++){
            for(int n=0; n<=q; n++){
                const double s0 = s[q][m][n];
                const double i0 = i[q][m][n];
                const int inf = m + n;
                const double gm = gamma_hype(inf, mu0, c);

                // 自身の感染/回復
                ds[q][m][n] += -lambda * (double)inf * s0 + gm * i0;
                di[q][m][n] += +lambda * (double)inf * s0 - gm * i0;

                // 近傍の感染：内部 m -> m+1（λ_in）
                ds[q][m][n] += -lambda_in_S * (double)(k - q - m) * s0;
                di[q][m][n] += -lambda_in_I * (double)(k - q - m) * i0;
                if(m > 0){
                    ds[q][m][n] += lambda_in_S * (double)(k - q - m + 1) * s[q][m-1][n];
                    di[q][m][n] += lambda_in_I * (double)(k - q - m + 1) * i[q][m-1][n];
                }

                // 近傍の感染：外部 n -> n+1（λ̄_out）
                ds[q][m][n] += -lambda_bar_out_S * (double)(q - n) * s0;
                di[q][m][n] += -lambda_bar_out_I * (double)(q - n) * i0;
                if(n > 0){
                    ds[q][m][n] += lambda_bar_out_S * (double)(q - n + 1) * s[q][m][n-1];
                    di[q][m][n] += lambda_bar_out_I * (double)(q - n + 1) * i[q][m][n-1];
                }

                // 近傍の回復：内部 m -> m-1（γ_in）
                ds[q][m][n] += -gamma_in_S * (double)m * s0;
                di[q][m][n] += -gamma_in_I * (double)m * i0;
                if(m < k - q){
                    ds[q][m][n] += gamma_in_S * (double)(m + 1) * s[q][m+1][n];
                    di[q][m][n] += gamma_in_I * (double)(m + 1) * i[q][m+1][n];
                }

                // 近傍の回復：外部 n -> n-1（γ̄_out）
                ds[q][m][n] += -gamma_bar_out_S * (double)n * s0;
                di[q][m][n] += -gamma_bar_out_I * (double)n * i0;
                if(n < q){
                    ds[q][m][n] += gamma_bar_out_S * (double)(n + 1) * s[q][m][n+1];
                    di[q][m][n] += gamma_bar_out_I * (double)(n + 1) * i[q][m][n+1];
                }
            }
        }
    }

    // ===== 4) ODE (26)(27): コミュニティ2 更新（外部の増減は G 側レートを使う） =====
    for(int q=0; q<=k_bar; q++){
        for(int m=0; m<=k_bar-q; m++){
            for(int n=0; n<=q; n++){
                const double s0 = s_bar[q][m][n];
                const double i0 = i_bar[q][m][n];
                const int inf = m + n;
                const double gm = gamma_hype(inf, mu0, c);

                ds_bar[q][m][n] += -lambda * (double)inf * s0 + gm * i0;
                di_bar[q][m][n] += +lambda * (double)inf * s0 - gm * i0;

                // 内部 m の増減は λ̄_in / γ̄_in
                ds_bar[q][m][n] += -lambda_bar_in_S * (double)(k_bar - q - m) * s0;
                di_bar[q][m][n] += -lambda_bar_in_I * (double)(k_bar - q - m) * i0;
                if(m > 0){
                    ds_bar[q][m][n] += lambda_bar_in_S * (double)(k_bar - q - m + 1) * s_bar[q][m-1][n];
                    di_bar[q][m][n] += lambda_bar_in_I * (double)(k_bar - q - m + 1) * i_bar[q][m-1][n];
                }

                // 外部 n の増減は G 側の λ_out / γ_out
                ds_bar[q][m][n] += -lambda_out_S * (double)(q - n) * s0;
                di_bar[q][m][n] += -lambda_out_I * (double)(q - n) * i0;
                if(n > 0){
                    ds_bar[q][m][n] += lambda_out_S * (double)(q - n + 1) * s_bar[q][m][n-1];
                    di_bar[q][m][n] += lambda_out_I * (double)(q - n + 1) * i_bar[q][m][n-1];
                }

                ds_bar[q][m][n] += -gamma_bar_in_S * (double)m * s0;
                di_bar[q][m][n] += -gamma_bar_in_I * (double)m * i0;
                if(m < k_bar - q){
                    ds_bar[q][m][n] += gamma_bar_in_S * (double)(m + 1) * s_bar[q][m+1][n];
                    di_bar[q][m][n] += gamma_bar_in_I * (double)(m + 1) * i_bar[q][m+1][n];
                }

                ds_bar[q][m][n] += -gamma_out_S * (double)n * s0;
                di_bar[q][m][n] += -gamma_out_I * (double)n * i0;
                if(n < q){
                    ds_bar[q][m][n] += gamma_out_S * (double)(n + 1) * s_bar[q][m][n+1];
                    di_bar[q][m][n] += gamma_out_I * (double)(n + 1) * i_bar[q][m][n+1];
                }
            }
        }
    }
}

double step(int k, int k_bar, double lambda, double mu0, double c){
    int q, m, n;

    /* 現在状態を保存 */
    for(q=0; q<=k; q++)
        for(m=0; m<=k-q; m++)
            for(n=0; n<=q; n++){
                s_old[q][m][n] = s[q][m][n];
                i_old[q][m][n] = i[q][m][n];
            }
    for(q=0; q<=k_bar; q++)
        for(m=0; m<=k_bar-q; m++)
            for(n=0; n<=q; n++){
                s_bar_old[q][m][n] = s_bar[q][m][n];
                i_bar_old[q][m][n] = i_bar[q][m][n];
            }

    /* RK4: k1 */
    compute_derivatives(k, k_bar, lambda, mu0, c);
    for(q=0; q<=k; q++)
        for(m=0; m<=k-q; m++)
            for(n=0; n<=q; n++){
                k1_s[q][m][n] = ds[q][m][n];
                k1_i[q][m][n] = di[q][m][n];
            }
    for(q=0; q<=k_bar; q++)
        for(m=0; m<=k_bar-q; m++)
            for(n=0; n<=q; n++){
                k1_s_bar[q][m][n] = ds_bar[q][m][n];
                k1_i_bar[q][m][n] = di_bar[q][m][n];
            }
    for(q=0; q<=k; q++)
        for(m=0; m<=k-q; m++)
            for(n=0; n<=q; n++){
                s[q][m][n] = s_old[q][m][n] + k1_s[q][m][n] * (dt * 0.5);
                i[q][m][n] = i_old[q][m][n] + k1_i[q][m][n] * (dt * 0.5);
            }
    for(q=0; q<=k_bar; q++)
        for(m=0; m<=k_bar-q; m++)
            for(n=0; n<=q; n++){
                s_bar[q][m][n] = s_bar_old[q][m][n] + k1_s_bar[q][m][n] * (dt * 0.5);
                i_bar[q][m][n] = i_bar_old[q][m][n] + k1_i_bar[q][m][n] * (dt * 0.5);
            }

    /* RK4: k2 */
    compute_derivatives(k, k_bar, lambda, mu0, c);
    for(q=0; q<=k; q++)
        for(m=0; m<=k-q; m++)
            for(n=0; n<=q; n++){
                k2_s[q][m][n] = ds[q][m][n];
                k2_i[q][m][n] = di[q][m][n];
            }
    for(q=0; q<=k_bar; q++)
        for(m=0; m<=k_bar-q; m++)
            for(n=0; n<=q; n++){
                k2_s_bar[q][m][n] = ds_bar[q][m][n];
                k2_i_bar[q][m][n] = di_bar[q][m][n];
            }
    for(q=0; q<=k; q++)
        for(m=0; m<=k-q; m++)
            for(n=0; n<=q; n++){
                s[q][m][n] = s_old[q][m][n] + k2_s[q][m][n] * (dt * 0.5);
                i[q][m][n] = i_old[q][m][n] + k2_i[q][m][n] * (dt * 0.5);
            }
    for(q=0; q<=k_bar; q++)
        for(m=0; m<=k_bar-q; m++)
            for(n=0; n<=q; n++){
                s_bar[q][m][n] = s_bar_old[q][m][n] + k2_s_bar[q][m][n] * (dt * 0.5);
                i_bar[q][m][n] = i_bar_old[q][m][n] + k2_i_bar[q][m][n] * (dt * 0.5);
            }

    /* RK4: k3 */
    compute_derivatives(k, k_bar, lambda, mu0, c);
    for(q=0; q<=k; q++)
        for(m=0; m<=k-q; m++)
            for(n=0; n<=q; n++){
                k3_s[q][m][n] = ds[q][m][n];
                k3_i[q][m][n] = di[q][m][n];
            }
    for(q=0; q<=k_bar; q++)
        for(m=0; m<=k_bar-q; m++)
            for(n=0; n<=q; n++){
                k3_s_bar[q][m][n] = ds_bar[q][m][n];
                k3_i_bar[q][m][n] = di_bar[q][m][n];
            }
    for(q=0; q<=k; q++)
        for(m=0; m<=k-q; m++)
            for(n=0; n<=q; n++){
                s[q][m][n] = s_old[q][m][n] + k3_s[q][m][n] * dt;
                i[q][m][n] = i_old[q][m][n] + k3_i[q][m][n] * dt;
            }
    for(q=0; q<=k_bar; q++)
        for(m=0; m<=k_bar-q; m++)
            for(n=0; n<=q; n++){
                s_bar[q][m][n] = s_bar_old[q][m][n] + k3_s_bar[q][m][n] * dt;
                i_bar[q][m][n] = i_bar_old[q][m][n] + k3_i_bar[q][m][n] * dt;
            }

    /* RK4: k4 および最終更新 y_new = y_old + (dt/6)*(k1 + 2*k2 + 2*k3 + k4) */
    compute_derivatives(k, k_bar, lambda, mu0, c);
    for(q=0; q<=k; q++)
        for(m=0; m<=k-q; m++)
            for(n=0; n<=q; n++){
                s[q][m][n] = s_old[q][m][n] + (dt / 6.0) * (k1_s[q][m][n] + 2.0*k2_s[q][m][n] + 2.0*k3_s[q][m][n] + ds[q][m][n]);
                i[q][m][n] = i_old[q][m][n] + (dt / 6.0) * (k1_i[q][m][n] + 2.0*k2_i[q][m][n] + 2.0*k3_i[q][m][n] + di[q][m][n]);
            }
    for(q=0; q<=k_bar; q++)
        for(m=0; m<=k_bar-q; m++)
            for(n=0; n<=q; n++){
                s_bar[q][m][n] = s_bar_old[q][m][n] + (dt / 6.0) * (k1_s_bar[q][m][n] + 2.0*k2_s_bar[q][m][n] + 2.0*k3_s_bar[q][m][n] + ds_bar[q][m][n]);
                i_bar[q][m][n] = i_bar_old[q][m][n] + (dt / 6.0) * (k1_i_bar[q][m][n] + 2.0*k2_i_bar[q][m][n] + 2.0*k3_i_bar[q][m][n] + di_bar[q][m][n]);
            }

    /* 集計 */
    double S_G = 0.0, I_G = 0.0;
    for(q=0; q<=k; q++)
        for(m=0; m<=k-q; m++)
            for(n=0; n<=q; n++){
                S_G += s[q][m][n];
                I_G += i[q][m][n];
            }
    double S_Gb = 0.0, I_Gb = 0.0;
    for(q=0; q<=k_bar; q++)
        for(m=0; m<=k_bar-q; m++)
            for(n=0; n<=q; n++){
                S_Gb += s_bar[q][m][n];
                I_Gb += i_bar[q][m][n];
            }
    const double denom = (double)N + (double)N_BAR;
    S = ((double)N * S_G + (double)N_BAR * S_Gb) / denom;
    I = ((double)N * I_G + (double)N_BAR * I_Gb) / denom;

    return I;
}


void evolve(double lambda,double mu0,double c,double rho,int k,int k_bar,int is_final){
    int itr=0;
    init(k,k_bar,rho);
    I=rho;
    double I_prev=rho;
    do {
        I_prev=I;
        ++itr;
        I=step(k,k_bar,lambda,mu0,c);
    } while (itr<100000 || fabs(I-I_prev)>1e-10);

    if (is_final){
        fprintf(fpdata, "%d,%.6f, %.6f, %.6f, %.6f, %.6f\n",n_r,lambda, mu0, c, rho, I);
    }
    else{
        double curtime=(double)itr*dt;
        fprintf(fpdata, "%d,%.6f, %.6f, %.6f, %.6f, %.6f, %.6f\n",n_r, curtime, lambda, mu0, c, rho, I);
    }
}

#define BAR_WIDTH 100

int main(void){
    int done, total;
    int is_final=1;

    double lambda_min=0.0;
    double lambda_max=0.1;
    double lambda_step=0.002;

    double mu0=1.0;
    double c_min=2.0;
    double c_max=2.0;
    double c_step=0.05;
    double rho=1.0;
    char fname[64];

    if(mkdir("ame", 0755) == -1 && errno != EEXIST){}
    if(mkdir("ame/sis", 0755) == -1 && errno != EEXIST){}
    if(mkdir("ame/sis/c=2.0", 0755) == -1 && errno != EEXIST){}

    char *z_str = malloc(64);
    sprintf(z_str, "ame/sis/c=2.0/z=%d_z_bar=%dn_r=%d", Z, Z_BAR, n_r);
    if(mkdir(z_str, 0755) == -1 && errno != EEXIST){}
    
    sprintf(fname, "%s/is_final=%s.csv", z_str, is_final==1 ? "true" : "false");

    fpdata=fopen(fname, "w");

    if(!fpdata){perror(fname); return 1;}
    if (is_final){
        fprintf(fpdata, "n_r,lambda,mu0,c,rho,I\n");
    }
    else{
        fprintf(fpdata, "n_r,time,lambda,mu0,c,rho,I\n");
    }

    /* 二重ループの格子点数 */
    int length_lambda=0;
    for(double l=lambda_min; l<=lambda_max+1e-12; l+=lambda_step) length_lambda++;
    int length_c=0;
    for(double cval=c_min; cval<=c_max+1e-12; cval+=c_step) length_c++;
    total=length_lambda*length_c;
    done=0;
    for(double lambda=lambda_min;lambda<=lambda_max+1e-12;lambda+=lambda_step){
        for(double c=c_min;c<=c_max+1e-12;c+=c_step){
            evolve(lambda,mu0,c,rho,Z,Z_BAR,is_final);
            ++done;
            {
                int b, n = (int)((double)done / total * BAR_WIDTH);
                fprintf(stderr, "\r[");
                for(b=0;b<BAR_WIDTH;b++){
                    fprintf(stderr, "%c", b < n ? '#' : '-');
                }
                fprintf(stderr, "] %d/%d", done, total);
                fflush(stderr);
            }
        }
    }
    fprintf(stderr, "\n");
    fclose(fpdata);
    free(z_str);
    return 0;
}
