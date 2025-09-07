#!/bin/bash

# App.javaを実行してS3にアップロードするスクリプト
# 使用方法: ./run-app-s3.sh [S3_BUCKET_NAME] [S3_PREFIX]

set -e  # エラー時に停止

# パラメータの設定
S3_BUCKET=${1:-"buturigakkai25"}
S3_PREFIX=${2:-"sis-simulation/$(date +%Y%m%d_%H%M%S)"}

echo "=========================================="
echo "SISシミュレーション実行 & S3アップロード"
echo "=========================================="
echo "S3バケット: $S3_BUCKET"
echo "S3プレフィックス: $S3_PREFIX"
echo "開始時刻: $(date)"
echo ""

# 1. Javaプロジェクトのビルドと実行
echo "1. Javaプロジェクトのビルドと実行..."
cd java-project

echo "  - Mavenクリーンとコンパイル..."
mvn clean compile

echo "  - シミュレーション実行..."
mvn exec:java

echo "  - 実行完了"
cd ..

# 2. 出力ディレクトリの確認
OUTPUT_DIR="java-project/output"
if [ ! -d "$OUTPUT_DIR" ]; then
    echo "エラー: 出力ディレクトリが見つかりません: $OUTPUT_DIR"
    exit 1
fi

echo ""
echo "2. 出力ファイルの確認..."
find "$OUTPUT_DIR" -type f -name "*.txt" -o -name "*.csv" | head -10
TOTAL_FILES=$(find "$OUTPUT_DIR" -type f -name "*.txt" -o -name "*.csv" | wc -l)
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

# AWS認証の確認

echo "  - ファイルのアップロード..."
aws s3 cp "$OUTPUT_DIR" "s3://$S3_BUCKET/$S3_PREFIX" --recursive

echo "  - アップロード完了"

# 4. 結果の表示

# アップロードされたファイルの一覧表示
echo "アップロードされたファイル:"
aws s3 ls "s3://$S3_BUCKET/$S3_PREFIX" --recursive | head -20
if [ $(aws s3 ls "s3://$S3_BUCKET/$S3_PREFIX" --recursive | wc -l) -gt 20 ]; then
    echo "... (他にもファイルがあります)"
fi

echo ""
echo "=========================================="
echo "完了時刻: $(date)"
echo "=========================================="