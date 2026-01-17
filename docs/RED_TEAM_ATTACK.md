# Red Team Attack: BatchResult Logic

## 1. Attack Vector: Serialization Bottleneck
- **Scenario**: Algorithm returns 1000 float points.
- **Current Logic**: Rust serializes to JSON -> C# deserialize -> display.
- **Attack**: `serde_json` overhead for 1000 objects. FFI crossing overhead.
- **Vulnerability**: Latency spike. UI freezing.

## 2. Attack Vector: Database Explosion
- **Scenario**: Continuous run (loop 10k times). Each step saves 1000 sub-results.
- **Attack**: 10k * 1000 = 10 Million records. `redb` file size growth rate?
- **Vulnerability**: Disk full. Write amplification. Slow queries.

## 3. Attack Vector: "All or Nothing" Retry
- **Scenario**: Point #55 failed due to glitch.
- **Attack**: User wants to retry ONLY #55.
- **Vulnerability**: Current architecture only supports retrying the WHOLE Step (re-capturing, re-calculating all 1000 points). This is inefficient and potentially dangerous (e.g., re-triggering a motor move).

## 4. Attack Vector: Variable Pool Contamination
- **Scenario**: 500 sub-checks each want to save their value to a variable?
- **Attack**: Naming collision. `voltage_1`, `voltage_2`... `voltage_500`. Variable pool becomes a garbage dump.
- **Vulnerability**: Variable management chaos.

## 5. Attack Vector: Real-time vs Post-processing
- **Scenario**: The algorithm takes 5 seconds.
- **Attack**: User stares at a blank screen for 5 seconds. No progressive update (e.g., "Processed 100/500...").
- **Vulnerability**: Poor UX. The `BatchResult` is atomic, meaning no partial updates.
