package io.github.ih0rd.benchmarks;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.NonNull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import io.github.ih0rd.adapter.context.PolyglotHelper;
import io.github.ih0rd.adapter.context.PyExecutor;
import io.github.ih0rd.adapter.spi.InMemoryScriptSource;
import io.github.ih0rd.polyglot.SupportedLanguage;
import io.github.ih0rd.polyglot.annotations.PolyglotClient;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms512m", "-Xmx2g"})
@Threads(1)
public class DataTransferBenchmark {
    @PolyglotClient(languages = SupportedLanguage.PYTHON)
    public interface TransferApi {
        int arrayLength(int[] values);

        int[] arrayMaterialize(int[] values);

        int[] listMaterialize(List<Integer> values);

        byte[] bufferMaterialize(ByteBuffer values);

        int[] pythonArrayResult();

        List<Object> pythonListResult();

        long pythonCompute();
    }

    @Param({"100", "1000", "100000"})
    public int size;

    private Context context;
    private PyExecutor executor;
    private TransferApi client;
    private Value pythonValues;
    private Value materialize;
    private int[] data;
    private List<Integer> list;
    private ByteBuffer heapBuffer;
    private ByteBuffer directBuffer;

    @Setup(Level.Trial)
    public void setup() {
        context = PolyglotHelper.newContext(SupportedLanguage.PYTHON);
        executor =
            new PyExecutor(context, new InMemoryScriptSource(Map.of("python/transfer_api", getString())));
        client = executor.bind(TransferApi.class);
        executor.validateBinding(TransferApi.class);
        Value target = context.getBindings("python").getMember("TransferApi");
        pythonValues = target.getMember("values");
        materialize = target.getMember("arrayMaterialize");
        data = new int[size];
        list = new ArrayList<>(size);
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = i;
            list.add(i);
            bytes[i] = (byte) i;
        }
        heapBuffer = ByteBuffer.wrap(bytes);
        directBuffer = ByteBuffer.allocateDirect(size);
        directBuffer.put(bytes).flip();
        if (client.arrayLength(data) != size
            || !Arrays.equals(client.arrayMaterialize(data), data)
            || !Arrays.equals(client.listMaterialize(list), data)
            || !Arrays.equals(client.bufferMaterialize(heapBuffer), bytes)
            || !Arrays.equals(client.bufferMaterialize(directBuffer), bytes)
            || !Arrays.equals(client.pythonArrayResult(), data)
            || ((Number) client.pythonListResult().get(size - 1)).intValue() != size - 1
            || client.pythonCompute() != (long) size * (size - 1) / 2
            || !Arrays.equals(pythonValues.as(int[].class), data)) {
            throw new IllegalStateException("Data transfer fixtures are not equivalent");
        }
    }

    private @NonNull String getString() {
        String script =
            """
                class TransferApi:
                    def __init__(self):
                        self.values = list(range(%d))
                    def arrayLength(self, values):
                        return len(values)
                    def arrayMaterialize(self, values):
                        return list(values)
                    def listMaterialize(self, values):
                        return list(values)
                    def bufferMaterialize(self, values):
                        return [values.get(i) for i in range(values.capacity())]
                    def pythonArrayResult(self):
                        return self.values
                    def pythonListResult(self):
                        return self.values
                    def pythonCompute(self):
                        return sum(self.values)
                # Retain the fixture target so GC does not recreate its prebuilt Python list.
                TransferApi = TransferApi()
                """
                .formatted(size);
        // A weak Java Value wrapper may be reclaimed; repeated source evaluation must not rebuild data.
        script = "if 'TransferApi' not in globals():\n" + script.indent(4);
        return script;
    }

    @TearDown(Level.Trial)
    public void close() {
        if (executor != null) executor.close();
        else if (context != null) context.close();
    }

    @Benchmark
    public int arrayLength() {
        return client.arrayLength(data);
    }

    @Benchmark
    public int[] arrayMaterialize() {
        return client.arrayMaterialize(data);
    }

    @Benchmark
    public int[] listMaterialize() {
        return client.listMaterialize(list);
    }

    @Benchmark
    public byte[] heapBufferMaterialize() {
        return client.bufferMaterialize(heapBuffer);
    }

    @Benchmark
    public byte[] directBufferMaterialize() {
        return client.bufferMaterialize(directBuffer);
    }

    @Benchmark
    public int[] pythonArrayResult() {
        return client.pythonArrayResult();
    }

    @Benchmark
    public List<?> pythonListResult() {
        return client.pythonListResult();
    }

    @Benchmark
    public long pythonCompute() {
        return client.pythonCompute();
    }

    @Benchmark
    public Value directGuestMaterialize() {
        return materialize.execute((Object) data);
    }

    @Benchmark
    public int[] directArrayResult() {
        return pythonValues.as(int[].class);
    }

    // Diagnostic for implicit Context entry overhead; conversion semantics stay with Value.as.
    @Benchmark
    public int[] enteredArrayResult() {
        context.enter();
        try {
            return pythonValues.as(int[].class);
        } finally {
            context.leave();
        }
    }
}
