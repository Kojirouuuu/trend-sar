#!/bin/bash

set -euo pipefail

# pemファイルは${KEY_PATH}/にあるものとする。

# 1. 環境変数の設定
# スクリプトをプロジェクトルートから実行することを前提とする
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# プロジェクトルートに移動
cd "$PROJECT_ROOT"

# .envファイルが存在する場合は読み込み、存在しない場合はデフォルト値を設定
if [ -f .env ]; then
    set -a
    source .env
    set +a
    echo "環境変数を.envファイルから読み込みました"
else
    echo "警告: .envファイルが見つかりません。"
    echo "環境変数を手動で設定するか、.envファイルを作成してください。"
fi

echo "設定値:"
echo "  KEY_PATH: $KEY_PATH"
echo "  YOUR_KEY: $YOUR_KEY"
echo "  EC2_USER: $EC2_USER"
echo "  EC2_HOST: $EC2_HOST"

# 3. 環境変数の検証
echo ""
echo "環境変数の検証中..."
ERROR_COUNT=0

if [ -z "$KEY_PATH" ]; then
    echo "エラー: KEY_PATH が設定されていません"
    ERROR_COUNT=$((ERROR_COUNT + 1))
fi

if [ -z "$YOUR_KEY" ]; then
    echo "エラー: YOUR_KEY が設定されていません"
    ERROR_COUNT=$((ERROR_COUNT + 1))
fi

if [ -z "$EC2_USER" ]; then
    echo "エラー: EC2_USER が設定されていません"
    ERROR_COUNT=$((ERROR_COUNT + 1))
fi

if [ -z "$EC2_HOST" ]; then
    echo "エラー: EC2_HOST が設定されていません"
    ERROR_COUNT=$((ERROR_COUNT + 1))
fi

if [ $ERROR_COUNT -gt 0 ]; then
    echo ""
    echo "=========================================="
    echo "エラー: $ERROR_COUNT 個の環境変数が設定されていません"
    echo "=========================================="
    echo ""
    echo "解決方法:"
    echo "1. .envファイルを作成して以下の内容を記述:"
    echo "   KEY_PATH=/path/to/your/ssh/keys"
    echo "   YOUR_KEY=your-key-name"
    echo "   EC2_USER=ec2-user"
    echo "   EC2_HOST=your-ec2-host"
    echo ""
    echo "2. または環境変数を直接設定:"
    echo "   export KEY_PATH=/path/to/your/ssh/keys"
    echo "   export YOUR_KEY=your-key-name"
    echo "   export EC2_USER=ec2-user"
    echo "   export EC2_HOST=your-ec2-host"
    echo ""
    exit 1
fi

echo "環境変数の検証完了: すべての変数が正しく設定されています"

# 2. KEY_PATH の ~ 展開と秘密鍵の存在確認・パーミッション設定
# .env で KEY_PATH に "~" を使った場合は絶対パスに展開する
if [[ "${KEY_PATH:-}" == ~* ]]; then
    KEY_PATH="${KEY_PATH/#~/$HOME}"
fi

PEM_PATH="$KEY_PATH/$YOUR_KEY.pem"

if [ ! -f "$PEM_PATH" ]; then
    echo "エラー: 秘密鍵が見つかりません: $PEM_PATH"
    exit 1
fi

chmod 400 "$PEM_PATH"

# 3. コードをEC2へアップロード（リモートのホームディレクトリへ）

# ローカル引数・環境変数から S3 パラメータを決定
DEFAULT_BUCKET="${BUCKET_NAME:-buturigakkai25}"
DEFAULT_PREFIX="twonet-simulation/$(date +%Y%m%d_%H%M%S)"
S3_BUCKET="${1:-$DEFAULT_BUCKET}"
S3_PREFIX="${2:-$DEFAULT_PREFIX}"

# 4. EC2にSSH接続し、リモートでビルド＆S3アップロードを実行
ssh -t -i "$PEM_PATH" "${EC2_USER}@${EC2_HOST}" bash -s -- "$S3_BUCKET" "$S3_PREFIX" << 'EOF'
set -euo pipefail

cd ~/trend-sar
pwd
git pull

# App.javaを実行してS3にアップロードするスクリプト
# 使用方法: 本スクリプトの引数で [S3_BUCKET_NAME] [S3_PREFIX] を受け取る

S3_BUCKET="${1:-buturigakkai25}"
S3_PREFIX="${2:-twonet-simulation/$(date +%Y%m%d_%H%M%S)}"

echo "=========================================="
echo "TwoNetSISシミュレーション実行 & S3アップロード"
echo "=========================================="
echo "S3バケット: $S3_BUCKET"
echo "S3プレフィックス: $S3_PREFIX"
echo "開始時刻: $(date)"
echo ""

# 1. Javaプロジェクトのビルドと実行
echo "1. Javaプロジェクトのビルドと実行..."
echo "現在のディレクトリ: $(pwd)"
cd java-project
echo "java-projectディレクトリに移動: $(pwd)"

echo "  - Mavenクリーンとコンパイル..."
mvn clean compile

echo "  - TwoNetSISシミュレーション実行..."
mvn exec:java -Dexec.mainClass="com.example.TwoNetSISApp"

echo "  - 実行完了"
cd ..
echo "プロジェクトルートに戻りました: $(pwd)"

# 2. 出力ディレクトリの確認
echo "java-projectディレクトリの内容を確認中..."
ls -la java-project/

OUTPUT_DIR="java-project/output"
echo "出力ディレクトリの確認: $OUTPUT_DIR"

if [ ! -d "$OUTPUT_DIR" ]; then
    echo "エラー: 出力ディレクトリが見つかりません: $OUTPUT_DIR"
    echo "現在のディレクトリ: $(pwd)"
    echo ""
    echo "java-projectディレクトリの詳細:"
    ls -la java-project/
    echo ""
    echo "App.javaが出力を試みたパスを確認してください"
    echo "期待されるパス: java-project/output/twonet/sis/RR/z=6/N=10000/"
    exit 1
fi

echo "出力ディレクトリが見つかりました: $OUTPUT_DIR"
echo "出力ディレクトリの内容:"
ls -la "$OUTPUT_DIR"

echo ""
echo "2. 出力ファイルの確認..."

# 現在のディレクトリと出力ディレクトリの内容を表示
echo "現在のディレクトリ: $(pwd)"
echo "出力ディレクトリの構造:"
find "$OUTPUT_DIR" -type d | head -10

# 出力ファイルを検索（-type f を両パターンに適用）
OUTPUT_FILES=$(find "$OUTPUT_DIR" -type f \( -name "*.txt" -o -name "*.csv" \) 2>/dev/null)

if [ -z "$OUTPUT_FILES" ]; then
    echo "エラー: 出力ファイルが見つかりません"
    echo "出力ディレクトリの詳細内容:"
    find "$OUTPUT_DIR" -type f | head -20
    echo ""
    echo "App.javaの出力パスを確認してください"
    exit 1
fi

echo "見つかったファイル:"
echo "$OUTPUT_FILES" | head -10
TOTAL_FILES=$(echo "$OUTPUT_FILES" | wc -l)
echo "  総ファイル数: $TOTAL_FILES"

# 3. S3へのアップロード
echo ""
echo "3. S3へのアップロード..."

# AWS CLIの確認
if ! command -v aws &> /dev/null; then
    echo "エラー: AWS CLIがインストールされていません"
    echo "インストール方法: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html"
    exit 1
fi

echo "  - ファイルのアップロード..."
if aws s3 cp "$OUTPUT_DIR" "s3://$S3_BUCKET/$S3_PREFIX" --recursive; then
    echo "  - アップロード完了"
    echo "  - ローカルファイルを削除中..."
    
    # アップロード成功後にローカルファイルを削除
    if [ -n "$OUTPUT_DIR" ] && [ -d "$OUTPUT_DIR" ]; then
        rm -rf "$OUTPUT_DIR"
        echo "  - ローカルファイル削除完了: $OUTPUT_DIR"
    else
        echo "  - 警告: 出力ディレクトリが見つかりません: $OUTPUT_DIR"
    fi
else
    echo "  - エラー: S3アップロードに失敗しました"
    echo "  - ローカルファイルは保持されます: $OUTPUT_DIR"
    exit 1
fi

echo ""
echo "=========================================="
echo "完了時刻: $(date)"
echo "=========================================="
EOF
