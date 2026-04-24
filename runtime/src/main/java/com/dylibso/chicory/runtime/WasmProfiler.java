package com.dylibso.chicory.runtime;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WasmProfiler {

    private static final Pattern FUNC_METHOD_PATTERN = Pattern.compile("^func_(\\d+)$");

    private final Thread targetThread;
    private final Machine machine;
    private final Duration interval;
    private final WasmProfile profile;
    private volatile boolean running;
    private Thread samplerThread;

    private WasmProfiler(Thread targetThread, Machine machine, Duration interval) {
        this.targetThread = targetThread;
        this.machine = machine;
        this.interval = interval;
        this.profile = new WasmProfile();
    }

    public static WasmProfiler create(Thread wasmThread, Machine machine) {
        return new WasmProfiler(wasmThread, machine, Duration.ofMillis(10));
    }

    public static WasmProfiler create(Thread wasmThread, Machine machine, Duration interval) {
        return new WasmProfiler(wasmThread, machine, interval);
    }

    public void start() {
        running = true;
        samplerThread = new Thread(this::sampleLoop, "wasm-profiler");
        samplerThread.setDaemon(true);
        samplerThread.start();
    }

    public WasmProfile stop() {
        running = false;
        if (samplerThread != null) {
            try {
                samplerThread.join(interval.toMillis() * 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return profile;
    }

    private void sampleLoop() {
        long intervalNanos = interval.toNanos();
        while (running) {
            long start = System.nanoTime();
            takeSample();
            long elapsed = System.nanoTime() - start;
            long sleepNanos = intervalNanos - elapsed;
            if (sleepNanos > 0) {
                try {
                    Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private void takeSample() {
        int[] stack = sampleStack();
        if (stack != null && stack.length > 0) {
            profile.recordSample(stack);
        }
    }

    private int[] sampleStack() {
        if (machine instanceof InterpreterMachine) {
            return ((InterpreterMachine) machine).sampleCallStack();
        }
        return sampleFromJvmStack();
    }

    private int[] sampleFromJvmStack() {
        StackTraceElement[] jvmStack = targetThread.getStackTrace();
        int count = 0;
        for (StackTraceElement frame : jvmStack) {
            if (FUNC_METHOD_PATTERN.matcher(frame.getMethodName()).matches()) {
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        int[] result = new int[count];
        int idx = 0;
        for (StackTraceElement frame : jvmStack) {
            Matcher m = FUNC_METHOD_PATTERN.matcher(frame.getMethodName());
            if (m.matches()) {
                result[idx++] = Integer.parseInt(m.group(1));
            }
        }
        return result;
    }
}
