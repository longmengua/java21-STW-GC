# java21-STW-GC

## 專案介紹

`java21-STW-GC` 是一個示範專案，用來觸發 Java GC（Garbage Collection）並觀察 **Stop-The-World (STW)** 暫停行為。  
專案使用 **Spring Boot 3.5 + Java 21**，可快速生成小堆、分配大量記憶體來觸發 GC。  
同時提供一個 shell 腳本，透過 **bpftrace** 監控 GC 開始/結束以及 Safepoint(STW) 暫停時間。

## 功能

- 觸發 Serial GC（小堆模式，容易觸發 GC/STW）
- 模擬大量記憶體分配，觀察 GC 行為
- 透過 **bpftrace** 監控：
    - `jdk.GCStart` / `jdk.GCEnd`
    - `jdk.SafepointBegin` / `jdk.SafepointEnd`
- 終端輸出每次 GC 與 STW 的耗時 (ms)

## 前置條件

- macOS
- Java 21（建議透過 jEnv 管理）
- Maven 3.8+

## 建置步驟

- 打包生成 jar 檔案
- 使用 shell 監控 GC/STW
  ```bash
  sudo ./run_gc_stw_monitor.sh --seconds=30 --use-pool
  
## 檢查執行

- 檢查是否有遺漏未被kill的執行個體
  ```bash
   ps -ef | grep GcStwApplication