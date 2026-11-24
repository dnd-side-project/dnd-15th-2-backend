# Gradle dependency scan

`Dependency Scan / dependency-scan`은 PR opened/synchronize/reopened와 수동 실행에서
실제 `runtimeClasspath` 전체를 검사한다. 직접·전이·runtimeOnly 의존성을 포함하며
test, compileOnly, annotationProcessor, 컨테이너 이미지와 SAST는 검사하지 않는다.

## 입력과 판정

CI 전용 init script가 locking을 활성화하고 `dependencyScanLock --write-locks`로
그 실행의 lockfile을 만든다. build/settings에 locking을 추가하거나 lockfile을
커밋하지 않는다. 해석 실패·빈 lock·해석 그래프/lock/scanner 패키지 집합 불일치는 실패다.
OSV-Scanner v2.7.1 바이너리와 action commit SHA를 고정한다.
scanner 배포물의 SHA-256도 검사한다.

OSV v2.7.1 `pkg/models/results.go`의 `results[].packages[]`와
`groups[].max_severity`를 사용한다. scanner가 계산한 그룹 최대 CVSS 수치가
7.0 이상이면 실패한다. 수치가 없으면 advisory `database_specific.severity`의
LOW/MODERATE/MEDIUM만 통과하며 HIGH/CRITICAL/unknown은 차단한다.
알 수 없는 벡터를 자체 추정하지 않는다. 별도 empty scanner config로 native ignore에
의한 누락을 방지한다. 취약 기능 미사용이나 수정 버전 부재는 자동 면제 사유가 아니다.

scanner 종료 코드 0은 무검출, 1은 검출이다. 그 외(127 일반 오류,
128 패키지 없음 포함), 비정상 JSON, 누락 결과, 300초 스캔 timeout은 도구/입력 실패다.
정책 스크립트 종료 코드는 0 통과, 1 미처리 취약점, 2 도구/입력 실패다.
예외로 도구 오류를 통과시킬 수 없다. job 전체 timeout은 10분이며 같은 ref의
이전 실행은 취소한다. API 장애 후 같은 commit을 재실행하고 이전 실패 기록을 보존한다.

## 예외

`config/security/dependency-exceptions.json`은 배열이다. 각 항목의 필수 필드는
`advisory`, `package`(Maven group:artifact), `version`(정확한 버전),
`reason`, `issue`(양의 정수), `expires_at`(UTC YYYY-MM-DD)이다.
예외는 사람이 PR에서 검토하며 30일 이내로 제한한다.
advisory 원본 ID 또는 alias와 package/version이 모두 일치해야 적용한다.
wildcard·중복·빈 필드·잘못된 타입은 실패하며 만료일 당일 00:00 UTC부터
해당 취약점 검출 여부와 무관하게 실패한다. 예외 연장도 새 리뷰가 필요하다.

현재 Spring MVC 6.2.19의 GHSA-j9f9-w8pj-32f8와 GHSA-pc63-qcmh-9cmg를
2026-12-07까지 한정한다. 공개 Maven 저장소의 6.2 계열은 6.2.19까지이며
advisory의 공개 수정 버전은 7.0.9다. Boot 4 마이그레이션은 이번 범위가 아니므로
이 예외는 위험 수용과 완화책에 대한 사람 리뷰가 필요하다. 이 등록은 취약점 수정이나
완화책 적용을 의미하지 않는다.

## 증거와 운영

artifact에는 원본 OSV JSON, scanner 버전·조회 시각·소요 시간·원본 ID/alias·예외
적용 결과를 담은 summary JSON, 해석 좌표 목록과 생성 lockfile을 30일 보관한다.
공개 API의 불변 DB snapshot 시각은 제공되지 않아 조회 시작 시각을 기록한다.
실패해도 artifact를 업로드하며 취소된 실행은 성공으로 취급하지 않는다.
권한은 `contents: read`만 사용하고 secret과 지속 checkout 자격 증명을 사용하지 않는다.

첫 일반/fork PR 실행 뒤 사람이 check 이름과 GitHub Actions app을 확인해 Ruleset의
required check로 등록해야 한다. workflow 추가만으로 merge 차단이 보장되지는 않는다.
CI cold/warm 각 3회에서 setup·해석·스캔·artifact 및 전체 시간을 측정해
중앙값/최댓값과 기존 CI critical path 증가를 비교한다.
초기 목표는 warm 중앙값 3분, cold 최대 10분이며 초과 시 원인과 변경안을 검토한다.

로컬에서는 저장소 밖의 main snapshot과 취약 runtimeOnly fixture를 사용한다.
정책 단위 검사는 `python3 scripts/test-dependency-scan.py`로 실행한다.
실제 실행 증거와 미검증 범위는 Issue #382 테스트 보고서에서 관리한다.
