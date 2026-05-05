package com.dylibso.chicory.bench;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wabt.Wat2Wasm;
import com.dylibso.chicory.wasm.Parser;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

// Compare: -Dchicory.hugeMethodLimit=1000000 (effectively no limit) vs default (8000)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Fork(1)
public class BenchmarkCallIndirectChunkSize {

    private static final int NUM_FUNCTIONS = 2000;

    @Param({"0", "999", "1999"})
    private int targetFunc;

    private ExportFunction caller;

    @Setup
    public void setup() {
        StringBuilder wat = new StringBuilder();
        wat.append("(module\n");
        wat.append("  (type $sig (func (param i32) (result i32)))\n");
        wat.append("  (table ").append(NUM_FUNCTIONS).append(" funcref)\n");

        for (int i = 0; i < NUM_FUNCTIONS; i++) {
            wat.append("  (func $f").append(i).append(" (type $sig)\n");
            wat.append("    local.get 0\n");
            wat.append("    i32.const ").append(i + 1).append("\n");
            wat.append("    i32.add)\n");
        }

        // elem segment to populate the table with all functions
        wat.append("  (elem (i32.const 0)");
        for (int i = 0; i < NUM_FUNCTIONS; i++) {
            wat.append(" $f").append(i);
        }
        wat.append(")\n");

        // exported caller: takes (value, table_index) and does call_indirect
        wat.append("  (func $call_indirect (export \"call_indirect\")")
                .append(" (param i32 i32) (result i32)\n");
        wat.append("    local.get 0\n");
        wat.append("    local.get 1\n");
        wat.append("    call_indirect (type $sig))\n");

        wat.append(")\n");

        byte[] wasm = Wat2Wasm.parse(wat.toString());
        Instance instance =
                Instance.builder(Parser.parse(wasm))
                        .withMachineFactory(MachineFactoryCompiler::compile)
                        .build();
        caller = instance.export("call_indirect");
    }

    @Benchmark
    public void callIndirect(Blackhole bh) {
        bh.consume(caller.apply(42, targetFunc));
    }
}
