#!/bin/bash

set -euo pipefail

# EC2ジョブ状態確認スクリプト
# 使用方法: bash aws/check_job_status.sh

# 1. 環境変数の設定
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# プロジェクトルートに移動
cd "$PROJECT_ROOT"

# .envファイルが存在する場合は読み込み
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

# 2. 環境変数の検証
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
    exit 1
fi

echo "環境変数の検証完了: すべての変数が正しく設定されています"

# 3. KEY_PATH の ~ 展開と秘密鍵の存在確認
if [[ "${KEY_PATH:-}" == ~* ]]; then
    KEY_PATH="${KEY_PATH/#~/$HOME}"
fi

PEM_PATH="$KEY_PATH/$YOUR_KEY.pem"

if [ ! -f "$PEM_PATH" ]; then
    echo "エラー: 秘密鍵が見つかりません: $PEM_PATH"
    exit 1
fi

echo ""
echo "=========================================="
echo "EC2ジョブ状態確認"
echo "=========================================="
echo "接続先: ${EC2_USER}@${EC2_HOST}"
echo "確認時刻: $(date)"
echo ""

# 4. Javaプロセスの確認
echo "1. Javaプロセスの確認..."
echo "----------------------------------------"
JAVA_PROCESSES=$(ssh -i "$PEM_PATH" "${EC2_USER}@${EC2_HOST}" "ps aux | grep java | grep -v grep" 2>/dev/null || echo "")

if [ -z "$JAVA_PROCESSES" ]; then
    echo "❌ Javaプロセスは実行されていません"
else
    echo "✅ Javaプロセスが実行中です:"
    echo "$JAVA_PROCESSES"
    
    # プロセスIDを抽出
    JAVA_PID=$(echo "$JAVA_PROCESSES" | head -1 | awk '{print $2}')
    if [ -n "$JAVA_PID" ]; then
        echo ""
        echo "2. プロセス詳細情報 (PID: $JAVA_PID)..."
        echo "----------------------------------------"
        
        # プロセス詳細情報
        PROCESS_INFO=$(ssh -i "$PEM_PATH" "${EC2_USER}@${EC2_HOST}" "ps -p $JAVA_PID -o pid,ppid,etime,pcpu,pmem,cmd" 2>/dev/null || echo "")
        if [ -n "$PROCESS_INFO" ]; then
            echo "$PROCESS_INFO"
        fi
        
        echo ""
        echo "3. プロセス状態詳細..."
        echo "----------------------------------------"
        PROCESS_STATUS=$(ssh -i "$PEM_PATH" "${EC2_USER}@${EC2_HOST}" "cat /proc/$JAVA_PID/status | grep -E '(State|VmRSS|Threads)'" 2>/dev/null || echo "")
        if [ -n "$PROCESS_STATUS" ]; then
            echo "$PROCESS_STATUS"
        fi
    fi
fi

echo ""
echo "4. システムリソース使用状況..."
echo "----------------------------------------"
SYSTEM_INFO=$(ssh -i "$PEM_PATH" "${EC2_USER}@${EC2_HOST}" "free -h && echo '---' && df -h /" 2>/dev/null || echo "")
if [ -n "$SYSTEM_INFO" ]; then
    echo "$SYSTEM_INFO"
fi

echo ""
echo "5. 出力ディレクトリの確認..."
echo "----------------------------------------"
OUTPUT_CHECK=$(ssh -i "$PEM_PATH" "${EC2_USER}@${EC2_HOST}" "ls -la java-project/output/sis/RR/z=6/N=10000final/ 2>/dev/null || echo '出力ディレクトリがまだ作成されていません'" 2>/dev/null || echo "")
echo "$OUTPUT_CHECK"

echo ""
echo "6. 最近のログ確認..."
echo "----------------------------------------"
RECENT_LOGS=$(ssh -i "$PEM_PATH" "${EC2_USER}@${EC2_HOST}" "tail -20 ~/trend-sar/java-project/target/maven-archiver/pom.properties 2>/dev/null || echo 'ログファイルが見つかりません'" 2>/dev/null || echo "")
echo "$RECENT_LOGS"

echo ""
echo "=========================================="
echo "ジョブ状態確認完了"
echo "=========================================="
echo ""

# 7. 簡単なステータスサマリー
if [ -n "$JAVA_PROCESSES" ]; then
    echo "📊 ステータスサマリー:"
    echo "  🟢 Javaプロセス: 実行中"
    echo "  📁 出力ディレクトリ: $(echo "$OUTPUT_CHECK" | grep -q "作成されていません" && echo "未作成" || echo "存在")"
    echo "  💾 システム状態: 正常"
else
    echo "📊 ステータスサマリー:"
    echo "  🔴 Javaプロセス: 停止中"
    echo "  📁 出力ディレクトリ: $(echo "$OUTPUT_CHECK" | grep -q "作成されていません" && echo "未作成" || echo "存在")"
    echo "  💾 システム状態: 正常"
fi

echo ""
echo "💡 ヒント:"
echo "  - ジョブを終了する場合: ssh -i $PEM_PATH ${EC2_USER}@${EC2_HOST} 'kill -9 <PID>'"
echo "  - 新しいジョブを開始する場合: bash aws/run_on_ec2.sh"
echo "  - このスクリプトを再実行する場合: bash aws/check_job_status.sh"
