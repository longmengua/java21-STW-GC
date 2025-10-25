package com.example.gc_stw;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * GC + Safepoint(STW) 監控示例 (Java 8 兼容)
 * 支援對比實驗：使用 Object Pool vs 不使用 Object Pool
 * 功能：
 *   1. 模擬內存分配，觸發 GC
 *   2. 收集堆內存使用、GC 次數和耗時
 *   3. 將數據輸出到 CSV 文件
 *   4. 運行指定秒數後自動停止
 *   5. 可選擇使用 Object Pool 來避免 STW
 */
public class GcStwApplication {

    // 簡單 byte[] 對象池
    static class ByteArrayPool {
        private final byte[][] pool;
        private final boolean[] used;

        public ByteArrayPool(int size, int blockSize) {
            pool = new byte[size][blockSize];
            used = new boolean[size];
        }

        // 取得空閒對象，若沒有則返回 null
        public synchronized byte[] acquire() {
            for (int i = 0; i < pool.length; i++) {
                if (!used[i]) {
                    used[i] = true;
                    return pool[i];
                }
            }
            return null;
        }

        // 釋放對象回池
        public synchronized void release(byte[] block) {
            for (int i = 0; i < pool.length; i++) {
                if (pool[i] == block) {
                    used[i] = false;
                    break;
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // 默認運行秒數
        int runSeconds = 20;
        boolean usePool = false;

        // 解析命令列參數
        for (String arg : args) {
            if (arg.startsWith("--seconds=")) {
                try {
                    runSeconds = Integer.parseInt(arg.split("=")[1]);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid seconds argument, using default 20 seconds");
                }
            } else if (arg.equals("--use-pool")) {
                usePool = true;
            }
        }

        // CSV 輸出檔案
        String csvFile = "./gc_stw_log.csv";

        System.out.printf("Start allocating memory to trigger GC for %d seconds%s...\n",
                runSeconds, usePool ? " (using Object Pool)" : "");

        // 初始化對象池（如果啟用）
        ByteArrayPool pool = null;
        if (usePool) {
            pool = new ByteArrayPool(10, 10 * 1024 * 1024); // 10 個 10MB 對象
        }

        try (FileWriter csvWriter = new FileWriter(csvFile)) {
            // CSV 標題
            csvWriter.append("Timestamp,HeapUsed,HeapMax,GCCount,EstimatedSTWTime(ms),UsePool\n");

            int iteration = 0;
            long startTime = System.currentTimeMillis();

            while (true) {
                iteration++;

                // 取得對象
                byte[] block;
                if (usePool) {
                    block = pool.acquire();
                    if (block == null) {
                        // 池滿，跳過這次分配
                        System.out.println("Pool exhausted, skipping allocation");
                        Thread.sleep(1000);
                        continue;
                    }
                } else {
                    // 每次新建 10MB 對象
                    block = new byte[10 * 1024 * 1024];
                }
                block[0] = 1; // 避免被 JVM 優化掉

                // 取得堆內存使用情況
                long heapUsed = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                long heapMax  = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();

                // 取得 GC 次數與耗時
                long totalGcCount = 0;
                long totalGcTime  = 0;
                List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
                for (GarbageCollectorMXBean gc : gcBeans) {
                    long count = gc.getCollectionCount();
                    long time  = gc.getCollectionTime();
                    if (count > 0) {
                        totalGcCount += count;
                        totalGcTime  += time;
                    }
                }

                // 當前時間
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                // CSV 寫入
                csvWriter.append(String.format("%s,%d,%d,%d,%d,%b\n",
                        timestamp, heapUsed, heapMax, totalGcCount, totalGcTime, usePool));
                csvWriter.flush();

                // 控制台輸出
                System.out.printf("[%d] %s | HeapUsed=%d, HeapMax=%d, GCCount=%d, EstimatedSTWTime=%d ms, UsePool=%b\n",
                        iteration, timestamp, heapUsed, heapMax, totalGcCount, totalGcTime, usePool);

                // 如果使用池，釋放對象
                if (usePool) {
                    pool.release(block);
                }

                // 檢查運行時間
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= runSeconds * 1000) {
                    System.out.println("Run time reached " + runSeconds + " seconds. Exiting...");
                    break;
                }

                // 每秒循環一次
                Thread.sleep(1000);
            }
        }
    }
}
