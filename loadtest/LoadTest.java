import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/// Usage: java loadtest/LoadTest.java [totalRequests] [concurrency]
/// Defaults: 5000 requests, 500 concurrent
public class LoadTest {

    private static final String BASE_URL = "http://localhost:8080/api/v1/notifications";
    private static final String[] CHANNELS = {"EMAIL", "SMS", "PUSH"};
    private static final String[] PRIORITIES = {"LOW", "MEDIUM", "HIGH", "URGENT"};

    // Counters
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);
    private static final AtomicInteger http202Count = new AtomicInteger(0);
    private static final AtomicInteger httpOtherCount = new AtomicInteger(0);

    // Latency tracking (in milliseconds)
    private static long[] latencies;

    public static void main(String[] args) throws Exception {
        int totalRequests = args.length >= 1 ? Integer.parseInt(args[0]) : 5000;
        int concurrency = args.length >= 2 ? Integer.parseInt(args[1]) : 500;

        latencies = new long[totalRequests];

        System.out.println("=============================================================");
        System.out.println("  Notification Service — Load Test");
        System.out.println("=============================================================");
        System.out.println("  Target:       " + BASE_URL);
        System.out.println("  Requests:     " + totalRequests);
        System.out.println("  Concurrency:  " + concurrency + " virtual threads");
        System.out.println("=============================================================");
        System.out.println();

        // Warm-up: send 1 request to ensure connection pool is ready
        System.out.println("[Warm-up] Sending 1 request...");
        try (HttpClient warmupClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {
            String warmupBody = buildPayload("warmup-key-0", "EMAIL", "HIGH");
            HttpRequest warmupReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", "warmup-key-0")
                    .POST(HttpRequest.BodyPublishers.ofString(warmupBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> warmupResp = warmupClient.send(warmupReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Warm-up] Response: HTTP " + warmupResp.statusCode());
        }
        System.out.println();

        // Create a shared HttpClient (connection pooling across virtual threads)
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger requestIndex = new AtomicInteger(0);

        System.out.println("[Load Test] Starting " + totalRequests + " requests with " + concurrency + " virtual threads...");
        Instant startTime = Instant.now();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Submit all tasks — virtual threads handle the concurrency
            // Use a semaphore-like approach: submit in batches of 'concurrency'
            for (int i = 0; i < totalRequests; i++) {
                final int idx = i;
                executor.submit(() -> {
                    sendRequest(client, idx, latencies);
                    latch.countDown();

                    // Progress update every 1000 requests
                    int completed = successCount.get() + failCount.get();
                    if (completed > 0 && completed % 1000 == 0) {
                        System.out.printf("  ... %d / %d completed (%.0f%%)%n",
                                completed, totalRequests, (completed * 100.0 / totalRequests));
                    }
                });
            }

            latch.await();
        }

        Instant endTime = Instant.now();
        long durationMs = Duration.between(startTime, endTime).toMillis();

        // Calculate latency percentiles
        int completed = successCount.get() + failCount.get();
        long[] validLatencies = Arrays.copyOf(latencies, completed);
        Arrays.sort(validLatencies);

        double rps = (completed * 1000.0) / durationMs;

        System.out.println();
        System.out.println("=============================================================");
        System.out.println("  RESULTS");
        System.out.println("=============================================================");
        System.out.printf("  Total Requests:    %d%n", totalRequests);
        System.out.printf("  Successful:        %d (HTTP calls that got a response)%n", successCount.get());
        System.out.printf("  Failed:            %d (connection errors / timeouts)%n", failCount.get());
        System.out.printf("  HTTP 202:          %d%n", http202Count.get());
        System.out.printf("  HTTP Other:        %d%n", httpOtherCount.get());
        System.out.println("-------------------------------------------------------------");
        System.out.printf("  Duration:          %.2f seconds%n", durationMs / 1000.0);
        System.out.printf("  Throughput:        %.0f req/sec%n", rps);
        System.out.println("-------------------------------------------------------------");
        if (validLatencies.length > 0) {
            System.out.printf("  Latency (min):     %d ms%n", validLatencies[0]);
            System.out.printf("  Latency (p50):     %d ms%n", percentile(validLatencies, 50));
            System.out.printf("  Latency (p95):     %d ms%n", percentile(validLatencies, 95));
            System.out.printf("  Latency (p99):     %d ms%n", percentile(validLatencies, 99));
            System.out.printf("  Latency (max):     %d ms%n", validLatencies[validLatencies.length - 1]);
        }
        System.out.println("=============================================================");
        System.out.println();
        System.out.println("Post-test verification commands:");
        System.out.println("  docker exec ns-postgres psql -U ns_user -d notification_db -c \"SELECT status, COUNT(*) FROM notification_log GROUP BY status;\"");
        System.out.println("  docker exec ns-redis redis-cli DBSIZE");

        client.close();
    }

    private static void sendRequest(HttpClient client, int index, long[] latencies) {
        String channel = CHANNELS[index % CHANNELS.length];
        String priority = PRIORITIES[index % PRIORITIES.length];
        String idempotencyKey = "load-" + System.nanoTime() + "-" + index;

        String body = buildPayload(idempotencyKey, channel, priority);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        long start = System.currentTimeMillis();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;
            latencies[index] = elapsed;

            if (response.statusCode() == 202) {
                http202Count.incrementAndGet();
            } else {
                httpOtherCount.incrementAndGet();
            }
            successCount.incrementAndGet();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            latencies[index] = elapsed;
            failCount.incrementAndGet();
        }
    }

    private static String buildPayload(String idempotencyKey, String channel, String priority) {
        String recipient = switch (channel) {
            case "EMAIL" -> "loadtest-" + idempotencyKey + "@example.com";
            case "SMS" -> "+1" + String.format("%010d", Math.abs(idempotencyKey.hashCode()) % 10_000_000_000L);
            case "PUSH" -> "device-token-" + idempotencyKey;
            default -> "test@example.com";
        };

        return """
                {
                  "channel": "%s",
                  "recipient": "%s",
                  "subject": "Load Test #%s",
                  "body": "Load test message for %s via %s",
                  "priority": "%s"
                }
                """.formatted(channel, recipient, idempotencyKey, recipient, channel, priority);
    }

    private static long percentile(long[] sorted, int p) {
        if (sorted.length == 0) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }
}
