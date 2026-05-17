# Contributing

[English](CONTRIBUTING.md) · [한국어](CONTRIBUTING.ko.md)

The full contributing guide — development setup, coding conventions, PR process, roadmap — lives at
**<https://api-log.devslab.kr/contributing/>**.

## Quick links

- 🐛 [Report a bug](https://github.com/devslab-kr/api-log/issues/new)
- 💡 [Suggest a feature](https://github.com/devslab-kr/api-log/issues/new)
- 📖 [Full development guide](https://api-log.devslab.kr/contributing/)
- 📋 [Roadmap](https://api-log.devslab.kr/contributing/#roadmap)

## TL;DR for code contributions

```bash
git clone https://github.com/devslab-kr/api-log.git
cd api-log
./mvnw verify           # build + unit tests + Testcontainers (needs Docker)
```

1. Fork → feature branch off `master`
2. Write tests for any behavior change
3. Update docs under `docs/` if you change a public API
4. Add a changelog entry to [docs/changelog.md](docs/changelog.md) under `[Unreleased]`
5. Open a PR against `master`

By contributing, you agree your contributions are licensed under [Apache 2.0](LICENSE).
