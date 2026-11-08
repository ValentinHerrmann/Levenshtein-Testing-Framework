# Maven example and template

The example exercise, protected by Ares 2 and built with Maven. The folder is standalone: copy it anywhere
(or use it as the start of your exam repository) and run it. [`example-gradle/`](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/tree/main/example-gradle) is the
same exercise built with Gradle.

```bash
./mvnw -B verify   # JDK 25
```

Inside the framework repository it compiles the framework source (profile `framework-source`). A copy uses the
newest framework release from Maven Central (`[2000.0.0,)`, release candidates included), so it also builds on
Artemis. To try a SNAPSHOT of the framework in a copy, run `./mvnw -B install -pl framework` in a clone of the
[framework repository](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework) and then
`./mvnw -B verify -Dlevenshtein.version=<snapshot>`.

To see what students see when their code is wrong, run the same tests against the failing example solution in
`assignment-failing/src`. It shows deviating types, modifiers, parameters and inheritance (some combined), a
typo, getter/setter reports and an exception in student code. The defects at a glance:
[`assignment-failing/README.md`](assignment-failing/README.md).

```bash
./mvnw -B verify -Dassignment.dir=assignment-failing   # fails on purpose
```

For a real exam, delete the self-checks `SecurityControlTest`, `TimeoutControlTest` and `SandboxControl`, the
`junit-platform-testkit` dependency (only `TimeoutControlTest` needs it) and the folder `assignment-failing`.

| Path | Content |
|---|---|
| `assignment/src` | student code (supervised by Ares, woven by AspectJ) |
| `assignment-failing/src` | failing example solution, selected by the command above |
| `test/` | instructor tests, wrappers, `SecurityPolicy.yaml` |
| `pom.xml` | Ares 2 wiring, reserved-package guard |

Setting up your own exam from it: [Quick Start](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/quick-start/). Why every setting exists:
[Ares 2 Setup in Detail](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/ares-setup/).
