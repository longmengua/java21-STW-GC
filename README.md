# java21-STW-GC

## 專案介紹

`java21-STW-GC` 是一個示範專案，用來觸發 Java GC（Garbage Collection）並觀察 **Stop-The-World (STW)** 暫停行為。  
專案使用 **Java 21**，可快速生成大量記憶體分配來觸發 GC，同時提供 Object Pool 與動態分配選項。  
透過 **console 輸出**或**shell 腳本**，可即時觀察 GC 次數、STW 時間、堆使用量與老年代累積情況。

---

## 功能

- 觸發 **新生代 GC（Minor GC）** 與 **老年代 GC（Major/Full GC）**
- 模擬大量記憶體分配，觀察 GC 行為
- 支援 **Object Pool** 減少頻繁大物件分配
- Console 即時輸出：
    - Heap Used / Heap Max
    - GC 次數 / STW 耗時
    - Pool 空閒數量 / 動態新增 block 數量
    - 老年代累積對象數量
- 支援命令列參數快速切換不同情境：
    - `--gc-type=minor|old`
    - `--use-pool`
    - `--seconds=<秒數>`

---

## 前置條件

- macOS / Linux
- Java 21（建議使用 jEnv 管理）
- Maven 3.8+
- bpftrace (可選，用於額外 GC/STW 追蹤)

---

## 建置步驟

- 打包生成 jar 檔案
- 使用 shell 監控 GC/STW
  ```bash
    # 預設 20 秒, 不使用 Object Pool, Minor GC
    ./run_gc_stw_monitor.sh
    
    # 指定運行 30 秒，使用 Object Pool，Minor GC
    ./run_gc_stw_monitor.sh 30 --use-pool minor
  
    # 指定運行 30 秒，使用 Object Pool，Minor GC
    ./run_gc_stw_monitor.sh 30 "" minor
    
    # 指定運行 30 秒，使用 Object Pool，Old GC
    ./run_gc_stw_monitor.sh 30 --use-pool old
    
    # 不使用 Pool，Old GC
    ./run_gc_stw_monitor.sh 30 "" old

  
## 檢查執行

- 檢查是否有遺漏未被kill的執行個體
  ```bash
   ps -ef | grep GcStwApplication