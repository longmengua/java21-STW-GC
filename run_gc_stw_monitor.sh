#!/bin/bash
# run_gc_stw_monitor.sh
# 啟動 GC + STW 監控 (CSV)，可指定運行秒數與是否使用 Object Pool
# 使用方法：
#   ./run_gc_stw_monitor.sh [秒數] [--use-pool]
#   例：
#       ./run_gc_stw_monitor.sh           -> 默認 20 秒，不使用 pool
#       ./run_gc_stw_monitor.sh 30        -> 30 秒，不使用 pool
#       ./run_gc_stw_monitor.sh 30 --use-pool  -> 30 秒，使用 pool

# CSV 輸出路徑
LOG_DIR="$HOME/gc_stw_logs"
mkdir -p "$LOG_DIR"
CSV_FILE="$LOG_DIR/gc_stw_log.csv"

# Java 可執行檔
JAVA_BIN=$(which java)

# 運行秒數 (預設 20 秒)
RUN_SECONDS=${1:-20}

# 是否使用 Object Pool
USE_POOL_OPTION=${2:-}

echo "啟動 GC + STW 監控 (CSV): $CSV_FILE"
echo "Java: $JAVA_BIN"
if [ -z "$USE_POOL_OPTION" ]; then
    echo "啟動 GC 監控應用 (運行 $RUN_SECONDS 秒，不使用 Object Pool)..."
else
    echo "啟動 GC 監控應用 (運行 $RUN_SECONDS 秒，使用 Object Pool)..."
fi

# 清空 CSV 文件
> "$CSV_FILE"

# 使用前台執行 Java，將 stdout/stderr 都導向 CSV
# 傳入秒數和 Object Pool 選項
"$JAVA_BIN" -jar ./gc-stw/target/gc-stw-1.0.0-SNAPSHOT.jar "$RUN_SECONDS" "$USE_POOL_OPTION" >> "$CSV_FILE" 2>&1 &

# 取得 PID
GC_PID=$!
echo "GC Monitor PID: $GC_PID"

# 即時顯示 CSV 文件內容
tail -f "$CSV_FILE"
