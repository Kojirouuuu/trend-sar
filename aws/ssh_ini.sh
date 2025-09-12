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

mkdir -p $KEY_PATH
chmod 700 $KEY_PATH

mv ~/Downloads/$YOUR_KEY.pem $KEY_PATH/
chmod 600 $KEY_PATH/$YOUR_KEY.pem
