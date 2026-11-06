# Gradle example and template

The example exercise, protected by Ares 2 and built with Gradle. The folder is standalone: copy it anywhere
(or use it as the start of your exam repository) and run it. [`example-maven/`](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/tree/main/example-maven) is the
same exercise built with Maven.

```bash
./gradlew test   # JDK 25
```

It uses the framework `2000.0.0` from Maven Central, so a copy also builds on Artemis. To try a SNAPSHOT of the
framework instead, run `./mvnw -B install -pl framework` in a clone of the
[framework repository](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework) and then
`./gradlew test -PlevenshteinVersion=<snapshot>`.

For a real exam, delete the self-checks `SecurityControlTest`, `TimeoutControlTest` and `SandboxControl` and the
`junit-platform-testkit` dependency (only `TimeoutControlTest` needs it).

| Path | Content |
|---|---|
| `assignment/src` | student code (supervised by Ares, woven by AspectJ) |
| `test/` | instructor tests, wrappers, `SecurityPolicy.yaml` |
| `build.gradle` | Ares 2 wiring, reserved-package guard |

Setting up your own exam from it: [Quick Start](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/blob/main/docs/quick-start.md). Why every setting exists:
[Ares 2 Setup in Detail](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/blob/main/docs/ares-setup.md).
