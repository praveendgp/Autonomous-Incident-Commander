import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ChaosService {

    private static final Logger logger = LogManager.getLogger(ChaosService.class);

    // Memory leak bucket
    private static final List<byte[]> MEMORY_LEAK_BUCKET = new ArrayList<>();

    // Load env
    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    // Chaos toggles
    private static final boolean ERRORS_ENABLED =
            Boolean.parseBoolean(getConfig("CHAOS_ERRORS", "true"));
    private static final boolean MEMORY_LEAK_ENABLED =
            Boolean.parseBoolean(getConfig("CHAOS_MEMORY_LEAK", "true"));
    private static final boolean CPU_SPIKE_ENABLED =
            Boolean.parseBoolean(getConfig("CHAOS_CPU_SPIKE", "true"));
    private static final boolean LATENCY_ENABLED =
            Boolean.parseBoolean(getConfig("CHAOS_LATENCY", "false"));
    private static final boolean NORMAL_WORK_ENABLED =
            Boolean.parseBoolean(getConfig("CHAOS_NORMAL_WORK", "true"));

    // Latency config
    private static final long LATENCY_MS =
            Long.parseLong(getConfig("CHAOS_LATENCY_MS", "1000"));
    private static final long LATENCY_JITTER_MS =
            Long.parseLong(getConfig("CHAOS_LATENCY_JITTER_MS", "0"));

    // Normal work config
    private static final long NORMAL_WORK_INTERVAL_MS =
            Long.parseLong(getConfig("NORMAL_WORK_INTERVAL_MS", "500"));
    private static final long NORMAL_WORK_LATENCY_MS =
            Long.parseLong(getConfig("NORMAL_WORK_LATENCY_MS", "50"));

    public static void main(String[] args) {

        logger.info(Map.of(
                "service.name", getConfig("SERVICE_NAME", "chaos-service"),
                "chaos.errors", ERRORS_ENABLED,
                "chaos.memory", MEMORY_LEAK_ENABLED,
                "chaos.cpu", CPU_SPIKE_ENABLED,
                "chaos.latency", LATENCY_ENABLED,
                "chaos.normal_work", NORMAL_WORK_ENABLED
        ));

        if (NORMAL_WORK_ENABLED)
            new Thread(ChaosService::normalWorkGenerator, "Normal-Work-Generator").start();

        if (ERRORS_ENABLED)
            new Thread(ChaosService::randomErrorGenerator, "Error-Generator").start();

        if (MEMORY_LEAK_ENABLED)
            new Thread(ChaosService::memoryLeakGenerator, "Memory-Leak-Generator").start();

        if (CPU_SPIKE_ENABLED)
            new Thread(ChaosService::cpuSpikeGenerator, "CPU-Spike-Generator").start();

        if (LATENCY_ENABLED)
            new Thread(ChaosService::latencyGenerator, "Latency-Generator").start();
    }

    // ---------------- NORMAL BEHAVIOR ----------------
    private static void normalWorkGenerator() {
        long requestId = 0;

        while (true) {
            attachTrace();
            requestId++;

            long start = System.currentTimeMillis();
            double result = 0;

            for (int i = 0; i < 10_000; i++) {
                result += Math.sqrt(i);
            }

            sleep(NORMAL_WORK_LATENCY_MS);

            long duration = System.currentTimeMillis() - start;

            logger.info(Map.of(
                    "event.category", "service",
                    "event.type", "request",
                    "request.id", requestId,
                    "request.duration_ms", duration,
                    "request.result", result
            ));

            sleep(NORMAL_WORK_INTERVAL_MS);
        }
    }

    // ---------------- ERROR CHAOS ----------------
    private static void randomErrorGenerator() {
        while (true) {
            attachTrace();
            sleepRandom(500, 3000);

            try {
                switch (ThreadLocalRandom.current().nextInt(4)) {
                    case 0 -> throw new RuntimeException("Injected RuntimeException");
                    case 1 -> { String s = null; s.length(); }
                    case 2 -> { int x = 1 / 0; }
                    case 3 -> throw new IllegalStateException("Injected IllegalStateException");
                }
            } catch (Exception e) {
                // FORCE "thrown"
                logger.error("", e);
            }
        }
    }

    // ---------------- MEMORY LEAK ----------------
    private static void memoryLeakGenerator() {
        while (true) {
            attachTrace();
            sleepRandom(200, 800);

            MEMORY_LEAK_BUCKET.add(new byte[1024 * 1024]);

            logger.warn(Map.of(
                    "event.category", "memory",
                    "event.type", "leak",
                    "memory.blocks", MEMORY_LEAK_BUCKET.size()
            ));
        }
    }

    // ---------------- CPU SPIKE ----------------
    private static void cpuSpikeGenerator() {
        while (true) {
            attachTrace();
            sleepRandom(1000, 5000);

            long duration = ThreadLocalRandom.current().nextLong(2000, 7000);
            long end = System.currentTimeMillis() + duration;

            logger.warn(Map.of(
                    "event.category", "cpu",
                    "event.type", "spike",
                    "cpu.spike.duration_ms", duration
            ));

            while (System.currentTimeMillis() < end) {
                Math.sqrt(ThreadLocalRandom.current().nextDouble());
            }
        }
    }

    // ---------------- LATENCY ----------------
    private static void latencyGenerator() {
        while (true) {
            attachTrace();

            long jitter = LATENCY_JITTER_MS > 0
                    ? ThreadLocalRandom.current().nextLong(0, LATENCY_JITTER_MS)
                    : 0;

            long delay = LATENCY_MS + jitter;

            logger.warn(Map.of(
                    "event.category", "latency",
                    "event.type", "inject",
                    "latency.ms", delay
            ));

            sleep(delay);
        }
    }

    // ---------------- UTIL ----------------
    private static void attachTrace() {
        ThreadContext.put("trace.id", UUID.randomUUID().toString().replace("-", ""));
        ThreadContext.put("span.id", UUID.randomUUID().toString().substring(0, 16));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static void sleepRandom(long min, long max) {
        sleep(ThreadLocalRandom.current().nextLong(min, max));
    }

    private static String getConfig(String key, String def) {
        String v = dotenv.get(key);
        return v != null ? v : System.getenv().getOrDefault(key, def);
    }
}
