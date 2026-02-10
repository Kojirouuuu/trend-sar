#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <errno.h>
#include <math.h>
#define Z 6
#define dt 0.005

FILE *fpdata;
double s[Z+1],ds[Z+1];//自身の次数がkで、mこの隣接iノードを持つsノードとその変化量
double i[Z+1],di[Z+1];//自身の次数がkで、mこの隣接iノードを持つiノードとその変化量
double S, I;

void init(int k,double rho){
    int l,m;
    double f_S, f_I;

    if (rho==1.0){
        for(m=0;m<=k-1;m++) s[m]=i[m]=0.;
        s[k]=0.0; i[k]=1.0;
        return;
    }

    for(m=0;m<=k;m++) s[m]=i[m]=0.;

    f_I=1;
    for(l=0;l<k;l++){
        f_I*=(1.-rho);
    }
    f_S=f_I*(1.-rho); f_I=f_I*rho;

    s[0]=f_S; i[0]=f_I;//k人の隣接ノード全員がsであるsノード、隣接ノード全員がsであるiノード

    for(m=1;m<=k;m++){
        double x = (k-m+1)*rho/m/(1.-rho);
        f_S*=x; f_I*=x;
        s[m] = f_S; i[m] = f_I;
    }
}

double step(int k,double lambda,double mu0,double c){
    int l,m;
    double sum_SS, sum_SI;
    double beta_S, beta_I;
    double sum_IS, sum_II;
    double gamma_S, gamma_I;

    sum_SS=sum_SI=0.; beta_S=beta_I=0.;
    sum_IS=sum_II=0.; gamma_S=gamma_I=0.;

    for(m=0;m<=k;m++){
        double x = s[m];
        l = k-m; // lは周りのsノードの数
        sum_SS += l*x; beta_S +=l*m*x;
        sum_SI += m*x; beta_I +=m*m*x;

        double y = i[m];
        double mu = mu0/(1.0+c*m);
        sum_IS += l*y; gamma_S += mu*l*y;
        sum_II += m*y; gamma_I += mu*m*y;

        ds[m] = di[m] = 0.;
    }

    if(sum_SS>0){beta_S=lambda*beta_S/sum_SS;}else{beta_S=0.;}
    if(sum_SI>0){beta_I=lambda*beta_I/sum_SI;}else{beta_I=0.;}
    if(sum_IS>0){gamma_S=gamma_S/sum_IS;}else{gamma_S=0.;}
    if(sum_II>0){gamma_I=gamma_I/sum_II;}else{gamma_I=0.;}

    for(m=0;m<=k;m++){
        int l = k-m;
        double x = lambda*m*s[m];
        double mu = mu0/(1.0+c*m);
        ds[m] += -x + mu*i[m];
        di[m] += x - mu*i[m];

        if(m<k){
            x = beta_S*l*s[m];
            ds[m+1] += x;ds[m] -= x;//sの隣接iノードがm人からm+1人に1人増える。

            x = beta_I*l*i[m];
            di[m+1] += x;di[m] -= x;//iの隣接iノードがm人からm+1人に1人増える。
        }

        if(m>0){
            x = gamma_S*m*s[m];
            ds[m] -= x;ds[m-1] += x;//sの隣接iノードがm人からm-1人に1人減る。

            x = gamma_I*m*i[m];
            di[m] -= x;di[m-1] += x;//iの隣接iノードがm人からm-1人に1人減る。
        }
    }

    S=I=0.;
    for(m=0;m<=k;m++){
        s[m] += ds[m]*dt;
        i[m] += di[m]*dt;
        S += s[m];
        I += i[m];
    }
    return I;
}

void evolve(double lambda,double mu0,double c,double rho, int k, int is_final){
    int itr=0;
    init(k,rho);
    I=rho;
    double I_prev=rho;
    do {
        I_prev=I;
        ++itr;
        I=step(k,lambda,mu0,c);
    } while (itr<10000 || fabs(I-I_prev)>0.0000001);

    if (is_final){
        fprintf(fpdata, "%.6f, %.6f, %.6f, %.6f, %.6f\n", lambda, mu0, c, rho, I);
    }
    else{
        double curtime=(double)itr*dt;
        fprintf(fpdata, "%.6f, %.6f, %.6f, %.6f, %.6f, %.6f\n", curtime, lambda, mu0, c, rho, I);
    }
}

#define BAR_WIDTH 40

int main(void){
    int done, total;
    int is_final=1;

    double lambda_min=0.0;
    double lambda_max=2.0;
    double lambda_step=0.002;

    double mu0_min=0.0;
    double mu0_max=10.0;
    double mu0_step=0.01;
    double c_min=0.0;
    double c_max=0.0;
    double c_step=0.01;
    double rho=1.0;
    char fname[64];

    if(mkdir("ame", 0755) == -1 && errno != EEXIST){}
    if(mkdir("ame/sis", 0755) == -1 && errno != EEXIST){}

    char *z_str = malloc(16);
    sprintf(z_str, "ame/sis/z=%d", Z);
    if(mkdir(z_str, 0755) == -1 && errno != EEXIST){}
    
    sprintf(fname, "%s/is_final=%s.csv", z_str, is_final==1 ? "true" : "false");

    fpdata=fopen(fname, "w");

    if(!fpdata){perror(fname); return 1;}
    if (is_final){
        fprintf(fpdata, "lambda,mu0,c,rho,I\n");
    }
    else{
        fprintf(fpdata, "time,lambda,mu0,c,rho,I\n");
    }

    total=0.0;
    for (double lambda=lambda_min;lambda<=lambda_max+1e-12;lambda+=lambda_step){
        for (double c=c_min;c<=c_max+1e-12;c+=c_step){
            for (double mu0=mu0_min;mu0<=mu0_max+1e-12;mu0+=mu0_step){
                total+=1;
            }
        }
    }
    
    done=0;
    for(double lambda=lambda_min;lambda<=lambda_max+1e-12;lambda+=lambda_step){
        for(double c=c_min;c<=c_max+1e-12;c+=c_step){
            for(double mu0=mu0_min;mu0<=mu0_max+1e-12;mu0+=mu0_step){
                evolve(lambda,mu0,c,rho,Z,is_final);
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
    }
    fprintf(stderr, "\n");
    fclose(fpdata);
    free(z_str);
    return 0;
}
