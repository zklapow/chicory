package com.dylibso.chicory.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dylibso.chicory.corpus.CorpusResources;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

public class WasmProfilerTest {

    private WasmModule loadModule(String fileName) {
        return Parser.parse(CorpusResources.getResource(fileName));
    }

    @Test
    public void itCollectsInterpreterSamples() throws Exception {
        Instance instance = Instance.builder(loadModule("compiled/iterfact.wat.wasm")).build();

        WasmProfiler profiler = WasmProfiler.create(Thread.currentThread(), instance.getMachine());
        profiler.start();

        ExportFunction iterFact = instance.exports().function("iterFact");
        for (int i = 0; i < 100_000; i++) {
            iterFact.apply(25);
        }

        WasmProfile profile = profiler.stop();

        assertTrue(profile.totalSamples() > 0, "Expected at least one sample");

        AtomicLong funcSamples = profile.flatSamples().get(0);
        assertTrue(
                funcSamples != null && funcSamples.get() > 0,
                "Expected samples for func 0 (iterFact)");
    }
}
