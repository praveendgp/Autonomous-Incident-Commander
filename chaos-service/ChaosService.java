import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.ObjectMessage;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ChaosService {

    private static final Logger logger = LogManager.getLogger(ChaosService.class);

    private static final List<byte[]> MEMORY_LEAK_BUCKET = new ArrayList<>();

    private static final Dotenv dotenv =
            Dotenv.configure().ignoreIfMissing().load();

    private static final boolean ERRORS_ENABLED =
            Boolean.parseBoolean(getConfig("CHAOS_ERRORS", "true"));
    private static final boolean MEMORY_LEAK_ENABLED =
            Boolean.parseBoolean(getConfig("CHAOS_MEMORY_LEAK", "true"));
    private static final boolean CPU_SPIKE_ENABLED =
            Boolean.parseBoolean(getConfig("CHAOS_CPU_SPIKE", "true"));

    public static void main(String[] args) {

        logger.info(new ObjectMessage(Map.of(
                "service.name", getConfig("SERVICE_NAME", "chaos-service"),
                "chaos.errors", ERRORS_ENABLED,
                "chaos.memory_leak", MEMORY_LEAK_ENABLED,
                "chaos.cpu_spike", CPU_SPIKE_ENABLED
        )));

        if (ERRORS_ENABLED)
            new Thread(ChaosService::randomErrorGenerator, "Error-Generator").start();
        if (MEMORY_LEAK_ENABLED)
            new Thread(ChaosService::memoryLeakGenerator, "Memory-Leak-Generator").start();
        if (CPU_SPIKE_ENABLED)
            new Thread(ChaosService::cpuSpikeGenerator, "CPU-Spike-Generator").start();
    }

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
                logger.error("",e); // OBJECT ONLY, STACK TRACE KEPT
            }
        }
    }

    private static void memoryLeakGenerator() {
        while (true) {
            attachTrace();
            sleepRandom(200, 800);

            MEMORY_LEAK_BUCKET.add(new byte[1024 * 1024]);

            logger.warn(new ObjectMessage(Map.of(
                    "event.category", "memory",
                    "event.type", "leak",
                    "memory.blocks", MEMORY_LEAK_BUCKET.size()
            )));
        }
    }

    private static void cpuSpikeGenerator() {
        while (true) {
            attachTrace();
            sleepRandom(1000, 5000);

            long duration = ThreadLocalRandom.current().nextLong(2000, 7000);
            long end = System.currentTimeMillis() + duration;

            logger.warn(new ObjectMessage(Map.of(
                    "event.category", "cpu",
                    "event.type", "start",
                    "cpu.spike.duration_ms", duration
            )));

            while (System.currentTimeMillis() < end) {
                Math.sqrt(ThreadLocalRandom.current().nextDouble());
            }

            logger.warn(new ObjectMessage(Map.of(
                    "event.category", "cpu",
                    "event.type", "end"
            )));
        }
    }

    private static void attachTrace() {
        ThreadContext.put("trace.id", UUID.randomUUID().toString().replace("-", ""));
        ThreadContext.put("span.id", UUID.randomUUID().toString().substring(0, 16));
    }

    private static void sleepRandom(long min, long max) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(min, max));
        } catch (InterruptedException ignored) {}
    }

    private static String getConfig(String key, String def) {
        String v = dotenv.get(key);
        return v != null ? v : System.getenv().getOrDefault(key, def);
    }
}
