# Contributing

Thanks for your interest in `api-log`. This is a small, focused starter — contributions are welcome especially for the items on the [Roadmap](#roadmap).

## Before you start

- **Check open issues** at [github.com/devslab-kr/api-log/issues](https://github.com/devslab-kr/api-log/issues) — your problem may already be tracked
- **For larger changes**, open a discussion or issue first so we can agree on the approach before you spend time
- **For small fixes** (typos, doc improvements, obvious bugs), just open a PR

## Development setup

### Prerequisites

- Java 21+ (the project uses `<java.version>21</java.version>`)
- Maven 3.9+ (or use the bundled `./mvnw` wrapper)
- Docker (for Testcontainers — PostgreSQL integration tests)
- Python 3.12+ (for `mkdocs serve` local docs preview)

### Clone and build

```bash
git clone https://github.com/devslab-kr/api-log.git
cd api-log
./mvnw verify
```

This compiles the library, runs unit tests, and runs the Testcontainers integration tests (Docker must be running).

### Preview the docs locally

```bash
pip install -r docs/requirements.txt
mkdocs serve
```

Visit [http://localhost:8000](http://localhost:8000). The site rebuilds on save.

## Coding conventions

- **Java style** — Spring Boot defaults; 4-space indent; line width is flexible (no enforced limit)
- **Lombok** is used — `@Getter`, `@RequiredArgsConstructor`, `@Builder`
- **Test naming** — `<ClassUnderTest>Test` for unit tests, `<ClassUnderTest>IntegrationTest` for Testcontainers-backed tests
- **Commit messages** — short imperative subject (`Add WebClient adapter`), explain the *why* in the body if non-obvious

## Submitting a PR

1. **Fork** the repo and create a feature branch off `master`
2. **Write tests** — every behavior change ships with tests
3. **Update docs** — if you add a public API or property, update the matching page in `docs/`
4. **Update the changelog** under `[Unreleased]` (see [Changelog](changelog.md))
5. **Open the PR** against `master` and fill in the description

We'll review within a few days for small PRs, longer for substantial ones.

## Roadmap

Items we'd love contributions on:

- [ ] `PUT`, `DELETE`, `PATCH` methods on `RestApiClientUtil`
- [ ] WebClient (reactive) variant of `RestApiClientUtil`
- [ ] Per-call header override API
- [ ] Configurable async executor (separate from Spring's default)
- [ ] Pluggable serialization (currently Jackson-only)
- [ ] Table partitioning helper (monthly `api_log_YYYYMM` partitions)

## Reporting bugs

[Open an issue](https://github.com/devslab-kr/api-log/issues/new) with:

- Spring Boot version, Java version, PostgreSQL version
- Minimal reproduction (smallest code/config that triggers the issue)
- Expected vs. actual behavior
- Full stack trace if applicable

## Code of conduct

Be respectful. Devslab follows the [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/) — harassment, discrimination, and trolling are not tolerated.

## License

By contributing, you agree your contributions are licensed under the [Apache License 2.0](https://github.com/devslab-kr/api-log/blob/master/LICENSE).
