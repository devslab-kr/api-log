# 기여하기

`api-log`에 관심 가져주셔서 감사합니다. 작고 집중된 스타터입니다 — 특히 아래 [로드맵](#roadmap)의 항목에 기여를 환영합니다.

## 시작하기 전에

- **열린 이슈 확인** [github.com/devslab-kr/api-log/issues](https://github.com/devslab-kr/api-log/issues) — 이미 추적 중인 문제일 수 있습니다
- **큰 변경**은 먼저 디스커션이나 이슈를 열어 접근 방식을 논의해주세요
- **작은 수정** (오타, 문서 개선, 명백한 버그)은 그냥 PR 보내주세요

## 개발 환경 셋업

### 필수

- Java 21+ (프로젝트는 `<java.version>21</java.version>` 사용)
- Maven 3.9+ (또는 번들된 `./mvnw` 사용)
- Docker (Testcontainers — PostgreSQL 통합 테스트용)
- Python 3.12+ (`mkdocs serve` 로컬 문서 프리뷰용)

### 클론 + 빌드

```bash
git clone https://github.com/devslab-kr/api-log.git
cd api-log
./mvnw verify
```

라이브러리 컴파일, 유닛 테스트, Testcontainers 통합 테스트까지 실행 (Docker가 실행 중이어야 함).

### 로컬 문서 프리뷰

```bash
pip install -r docs/requirements.txt
mkdocs serve
```

[http://localhost:8000](http://localhost:8000) 접속. 저장 시 자동 리빌드.

## 코딩 컨벤션

- **Java 스타일** — Spring Boot 기본값. 4칸 들여쓰기. 줄 너비 유연 (강제 제한 없음)
- **Lombok** 사용 — `@Getter`, `@RequiredArgsConstructor`, `@Builder`
- **테스트 명명** — 유닛 테스트는 `<ClassUnderTest>Test`, Testcontainers 기반은 `<ClassUnderTest>IntegrationTest`
- **커밋 메시지** — 짧은 명령형 제목 (`Add WebClient adapter`). 명확하지 않은 *왜*는 본문에

## PR 제출

1. 리포 **포크**해서 `master`에서 피처 브랜치 생성
2. **테스트 작성** — 동작 변경은 모두 테스트와 함께
3. **문서 업데이트** — 공개 API나 속성을 추가했다면 `docs/`의 해당 페이지도 업데이트
4. [Changelog](changelog.md)의 `[Unreleased]` 섹션에 **변경 이력 업데이트**
5. `master`에 **PR을 열고** 설명 작성

작은 PR은 며칠, 큰 PR은 더 걸릴 수 있습니다.

## 로드맵 {#roadmap}

기여를 환영하는 항목들:

- [ ] `RestApiClientUtil`의 `PUT`, `DELETE`, `PATCH` 메서드
- [ ] WebClient (리액티브) `RestApiClientUtil` 변형
- [ ] 호출별 헤더 오버라이드 API
- [ ] 설정 가능한 비동기 executor (Spring 기본과 분리)
- [ ] 플러그가능한 직렬화 (현재 Jackson만)
- [ ] 테이블 파티셔닝 헬퍼 (월별 `api_log_YYYYMM` 파티션)

## 버그 신고

[이슈 열기](https://github.com/devslab-kr/api-log/issues/new) 시 포함:

- Spring Boot 버전, Java 버전, PostgreSQL 버전
- 최소 재현 (문제를 일으키는 가장 작은 코드/설정)
- 예상 vs 실제 동작
- 가능하면 전체 스택 트레이스

## 행동 강령

서로 존중합시다. Devslab은 [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/)를 따릅니다 — 괴롭힘, 차별, 트롤링은 용납되지 않습니다.

## 라이선스

기여 시 [Apache License 2.0](https://github.com/devslab-kr/api-log/blob/master/LICENSE) 아래 라이선스에 동의하는 것으로 간주됩니다.
