# Slang

Slang is a small educational programming language implemented in Java/Kotlin with ANTLR for parsing. This README explains how to get the codebase running locally and how to start contributing.

## Quick start

1. Install prerequisites:
   - JDK 21 or later.
   - No global Gradle required; use the included wrapper `./gradlew`.
2. Clone the repository:
   ```bash
   git clone https://github.com/jpksh90/Slang.git
   cd Slang
   ```
3. Build the project (this generates ANTLR sources and compiles):
  ```bash
   ./gradlew clean build
  ```
4. Run tests:
  ```bash
   ./gradlew test
  ```

## Open in IntelliJ IDEA (macOS)

- File > Open... > select the repository root (or the `build.gradle` file).
- Enable Gradle auto-import when prompted.
- If the IDE does not recognize generated sources, run:
  ```bash
  ./gradlew generateGrammarSource
  ./gradlew build
  ```
- To run or debug the REPL or the main application, create an Application run configuration that points to the appropriate `main` class or use the Gradle task `run`.

## Useful commands

- Run the REPL:
```bash
./slang repl
# Or using Gradle:
./gradlew run --console=plain --quiet --args="repl"
```
- Run a Slang program:
  ```bash
  ./slang <input-file>
  # Example:
  ./slang src/test/resources/sum_prod.slang
  ```
- Output HLIR representation:
  ```bash
  # To stdout:
  ./slang --hlir src/test/resources/sum_prod.slang
  # To file:
  ./slang --hlir src/test/resources/sum_prod.slang -o output.yaml
  ```

- Clean build artifacts:
```bash
  ./gradlew clean
```

## Project layout (important files)

- `src/main/antlr/Slang.g4` — ANTLR grammar.
- `src/main/kotlin/slast/ast/AstBuilder.kt` — AST builder from parse tree.
- `build/generated-src/antlr/main/SlangParser.java` — generated parser (do not edit).
- `src/test/resources/` — example `.slang` programs used by tests.

## Development notes

- Grammar changes: edit `src/main/antlr/Slang.g4` and regenerate:
```bash
  ./gradlew generateGrammarSource
  ./gradlew build
``` 
- Tests exercise language features. Run them frequently when changing parser/AST/IR.
- Keep generated parser files out of manual edits; regenerate from the .g4 source.

## Contributing

- Create a feature branch from `main`.
- Ensure `./gradlew build` and `./gradlew test` pass locally before opening a PR.
- Use small, focused commits and describe the intent in the PR.

## Troubleshooting

- If IntelliJ can't see generated sources: run `./gradlew generateGrammarSource` then refresh Gradle projects.
- If Gradle fails due to JDK version, confirm `java -version` shows JDK 21+ and adjust `IDEA Project SDK` if needed.

## Contact

- Open issues or pull requests on GitHub at the repository root.

## AI Disclaimer
- AI was heavily used to generate the code.