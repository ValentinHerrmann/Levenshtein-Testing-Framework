# Gradle template

The Gradle twin of [`example/`](../example): the same exercise, protected by Ares 2, built with Gradle.
Copy [`build.gradle`](build.gradle) and [`test/SecurityPolicy.yaml`](test/SecurityPolicy.yaml) when you set up
a Gradle exam repository, and read [docs/ares-setup.md](../docs/ares-setup.md) for the reasons behind every
setting.

To keep one exercise, the Java sources are shared with `example/` (see `sourceSets` in `build.gradle`). In an
exam repository they live in `assignment/src` and `test` next to `build.gradle`.

```bash
./mvnw -B install -pl framework       # once: the framework SNAPSHOT into ~/.m2
cd example-gradle && ./gradlew test   # JDK 25
```
