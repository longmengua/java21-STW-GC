package com.example.gc_stw;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 模擬 Java GC 行為（新生代 / 老年代）
 * 功能：
 *   1. 支援使用 Object Pool 或直接分配
 *   2. 模擬新生代 GC（Minor GC）與老年代 GC（Major/Full GC）
 *   3. 支援池耗盡時是否允許動態新增 block
 *   4. Console 輸出堆使用、GC 次數、STW 時間、Pool 狀態、老年代累積
 */
public class GcStwApplication {

    /**
     * 簡單 byte[] 對象池
     */
    static class ByteArrayPool {
        private final byte[][] pool; // 對象池陣列
        private final boolean[] used; // 對象是否被占用

        public ByteArrayPool(int size, int blockSize) {
            pool = new byte[size][blockSize]; // 一次性分配 block
            used = new boolean[size];         // 初始都為空閒
        }

        /** 取得空閒 block，如果沒有則返回 null */
        public synchronized byte[] acquire() {
            for (int i = 0; i < pool.length; i++) {
                if (!used[i]) {
                    used[i] = true;
                    return pool[i];
                }
            }
            return null;
        }

        /** 釋放 block 回池 */
        public synchronized void release(byte[] block) {
            for (int i = 0; i < pool.length; i++) {
                if (pool[i] == block) {
                    used[i] = false;
                    break;
                }
            }
        }

        /** 取得目前池中空閒 block 數量 */
        public synchronized int getFreeCount() {
            int count = 0;
            for (boolean u : used) if (!u) count++;
            return count;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // 預設運行秒數、是否使用 pool、是否允許動態新增 block
        int runSeconds = 20;
        boolean usePool = false;
        boolean allowDynamicAllocate = true;
        String gcType = "minor"; // 預設只觸發新生代 GC

        // 解析命令列參數
        for (String arg : args) {
            if (arg.startsWith("--seconds=")) runSeconds = Integer.parseInt(arg.split("=")[1]);
            else if (arg.equals("--use-pool")) usePool = true;
            else if (arg.equals("--no-dynamic-allocate")) allowDynamicAllocate = false;
            else if (arg.startsWith("--gc-type=")) gcType = arg.split("=")[1].toLowerCase();
        }

        System.out.printf("Running %d seconds | UsePool=%b | AllowDynamic=%b | GC Type=%s\n",
                runSeconds, usePool, allowDynamicAllocate, gcType);

        // 初始化對象池
        ByteArrayPool pool = null;
        if (usePool) pool = new ByteArrayPool(10, 10 * 1024 * 1024);

        // 用於老年代情境，累積大量大對象
        List<byte[]> oldGenHolder = new ArrayList<>();

        int dynamicBlocks = 0; // 統計池耗盡時動態新增的 block 數量

        // 啟動工作線程，模擬小對象分配（觸發新生代 GC）
        int numThreads = 4;
        for (int t = 0; t < numThreads; t++) {
            new Thread(() -> {
                while (true) {
                    // 分配 1MB 小對象
                    byte[] temp = new byte[1 * 1024 * 1024];
                    temp[0] = 1; // 避免 JVM 優化掉
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                }
            }, "Worker-" + t).start();
        }

        int iteration = 0;
        long startTime = System.currentTimeMillis();

        while (true) {
            iteration++;
            long loopStart = System.currentTimeMillis();

            // 取得 block
            byte[] block = null;

            if (usePool) {
                block = pool.acquire();
                if (block == null) {
                    if (allowDynamicAllocate) {
                        // 池耗盡 → 動態分配新 block
                        block = new byte[10 * 1024 * 1024];
                        dynamicBlocks++;
                        System.out.println("Pool exhausted, dynamically allocated new block");
                    } else {
                        // 池耗盡且不允許動態新增 → 跳過本次迴圈
                        Thread.sleep(500);
                        continue;
                    }
                }
            } else {
                // 不使用池 → 每次分配 10MB
                block = new byte[10 * 1024 * 1024];
            }
            block[0] = 1; // 避免 JVM 優化掉

            // 如果設定為老年代 GC 情境，累積 block
            if ("old".equals(gcType)) {
                oldGenHolder.add(block);
            } else {
                // minor GC 情境，不保留 block，立即可被回收
                block = null;
            }

            // 取得堆使用資訊
            long heapUsed = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long heapMax = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();

            // 取得 GC 次數與耗時
            long totalGcCount = 0;
            long totalGcTime = 0;
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            for (GarbageCollectorMXBean gc : gcBeans) {
                long count = gc.getCollectionCount();
                long time = gc.getCollectionTime();
                if (count > 0) { totalGcCount += count; totalGcTime += time; }
            }

            // 計算迴圈耗時
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            long loopEnd = System.currentTimeMillis();
            long loopElapsed = loopEnd - loopStart;

            // Pool 空閒數量
            int poolFree = usePool ? pool.getFreeCount() : 0;

            // Console 輸出
            System.out.printf("[%d] %s | HeapUsed=%d, HeapMax=%d, GCCount=%d, STWTime=%d ms, LoopElapsed=%d ms, PoolFree=%d, DynamicBlocks=%d, OldHolderSize=%d\n",
                    iteration, timestamp, heapUsed, heapMax, totalGcCount, totalGcTime, loopElapsed,
                    poolFree, dynamicBlocks, oldGenHolder.size());

            // 如果使用池，釋放 block 回池
            if (usePool && block != null && block.length == 10 * 1024 * 1024) pool.release(block);

            // 檢查是否達到運行時間
            if (System.currentTimeMillis() - startTime >= runSeconds * 1000) {
                System.out.println("Run time reached. Exiting...");
                break;
            }

            // 每秒迴圈一次
            Thread.sleep(1000);
        }
    }
}
