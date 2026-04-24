package com.dylibso.chicory.runtime;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class WasmProfile {

    private final ConcurrentHashMap<Integer, AtomicLong> flatSamples = new ConcurrentHashMap<>();
    private final AtomicLong totalSamples = new AtomicLong();

    void recordSample(int[] stack) {
        totalSamples.incrementAndGet();
        for (int funcId : stack) {
            flatSamples.computeIfAbsent(funcId, k -> new AtomicLong()).incrementAndGet();
        }
    }

    public long totalSamples() {
        return totalSamples.get();
    }

    public Map<Integer, AtomicLong> flatSamples() {
        return flatSamples;
    }

    public void printFlat(PrintStream out, Instance instance) {
        long total = totalSamples.get();
        if (total == 0) {
            out.println("No samples collected.");
            return;
        }

        List<Map.Entry<Integer, AtomicLong>> sorted = new ArrayList<>(flatSamples.entrySet());
        sorted.sort(
                Comparator.<Map.Entry<Integer, AtomicLong>>comparingLong(e -> e.getValue().get())
                        .reversed());

        out.printf("%-8s %-7s %s%n", "samples", "%", "function");
        out.printf("%-8s %-7s %s%n", "-------", "------", "--------");
        for (Map.Entry<Integer, AtomicLong> entry : sorted) {
            long count = entry.getValue().get();
            double pct = 100.0 * count / total;
            String name = resolveFunctionName(entry.getKey(), instance);
            out.printf("%-8d %5.1f%%  %s%n", count, pct, name);
        }
        out.printf("%nTotal samples: %d%n", total);
    }

    private static String resolveFunctionName(int funcId, Instance instance) {
        if (instance != null && instance.module().nameSection() != null) {
            String name = instance.module().nameSection().nameOfFunction(funcId);
            if (name != null) {
                return name + " [" + funcId + "]";
            }
        }
        return "func_" + funcId;
    }
}
