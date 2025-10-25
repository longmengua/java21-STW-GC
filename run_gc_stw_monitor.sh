#!/bin/bash
# run_gc_stw_monitor.sh
# 啟動 GC + STW 監控 (console 版本)，可指定運行秒數、Object Pool 與 GC 類型
# 使用方法：
#   ./run_gc_stw_monitor.sh [秒數] [--use-pool] [minor|old] [最大堆大小]

JAVA_BIN=$(which java)

# 運行秒數 (預設 20 秒)
RUN_SECONDS=${1:-20}
# 是否使用 Object Pool
USE_POOL_OPTION=${2:-}
# GC 類型，預設 minor
GC_TYPE_OPTION=${3:-minor}
# JVM 最大堆大小
MAX_HEAP=${4:-128m}
# JVM 初始堆大小，與最大堆相同
MIN_HEAP=$MAX_HEAP

echo "啟動 GC + STW 監控 (console)"
echo "Java: $JAVA_BIN"
echo "運行 $RUN_SECONDS 秒 | UsePool: ${USE_POOL_OPTION:-false} | GC Type: $GC_TYPE_OPTION | MaxHeap: $MAX_HEAP"

# 判斷 Java 版本，決定用 verbose 或 Xlog
JAVA_VERSION=$("$JAVA_BIN" -version 2>&1 | awk -F '"' '/version/ {print $2}')
JAVA_MAJOR=$(echo $JAVA_VERSION | awk -F. '{print $1}')

if [ "$JAVA_MAJOR" -ge 9 ]; then
    # Java 9+ 使用 Xlog
    JVM_GC_LOG_OPTS="-Xlog:gc*:stdout:time,uptime,level,tags"
else
    # Java 8 使用 verbose
    JVM_GC_LOG_OPTS="-verbose:gc -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCApplicationConcurrentTime"
fi

# 強制快速觸發 Old GC 的 JVM 參數 (G1GC 範例)
# 1. 固定堆大小
# 2. 老年代觸發閾值設低 (20%)
JVM_OLD_GC_OPTS="-Xms$MIN_HEAP -Xmx$MAX_HEAP -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=20"

# 執行 Java 程式（直接 console 輸出）
"$JAVA_BIN" $JVM_OLD_GC_OPTS $JVM_GC_LOG_OPTS -jar ./gc-stw/target/gc-stw-1.0.0-SNAPSHOT.jar \
    --seconds="$RUN_SECONDS" \
    $USE_POOL_OPTION \
    --gc-type="$GC_TYPE_OPTION"
