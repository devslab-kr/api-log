# 기여하기

[English](CONTRIBUTING.md) · [한국어](CONTRIBUTING.ko.md)

개발 환경, 코딩 컨벤션, PR 프로세스, 로드맵 등 전체 기여 가이드는
**<https://api-log.devslab.kr/ko/contributing/>** 를 참고하세요.

## 빠른 링크

- 🐛 [버그 신고](https://github.com/devslab-kr/api-log/issues/new)
- 💡 [기능 제안](https://github.com/devslab-kr/api-log/issues/new)
- 📖 [전체 개발 가이드](https://api-log.devslab.kr/ko/contributing/)
- 📋 [로드맵](https://api-log.devslab.kr/ko/contributing/#roadmap)

## 코드 기여 TL;DR

```bash
git clone https://github.com/devslab-kr/api-log.git
cd api-log
./mvnw verify           # 빌드 + 유닛 테스트 + Testcontainers (Docker 필요)
```

1. 포크 → `master`에서 피처 브랜치
2. 동작 변경 시 테스트 작성
3. 공개 API 변경 시 `docs/`의 해당 문서도 업데이트
4. [docs/changelog.md](docs/changelog.md)의 `[Unreleased]`에 변경 이력 추가
5. `master`에 PR 열기

기여 시 [Apache 2.0](LICENSE) 라이선스에 동의하는 것으로 간주됩니다.
