# 変数設定
# URL="s3://buturigakkai25/sis-simulation/20250909_225657/sis/"
URL="s3://buturigakkai25/ec2-simulation/20250912_174815/sis/"
LOCAL_DIR="downloaded"     # 保存先ディレクトリ

# ローカルの保存先ディレクトリを作成
mkdir -p "$LOCAL_DIR"

# s3からディレクトリごとコピー
aws s3 cp $URL $LOCAL_DIR --recursive
