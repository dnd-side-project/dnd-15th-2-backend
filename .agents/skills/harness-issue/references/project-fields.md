# GitHub Project 필드 캐시

Project: **Qello Backend Roadmap** (`dnd-side-project` org, number `141`)

| 항목 | 값 |
| --- | --- |
| Project ID | `PVT_kwDOBD3v1M4BfKwo` |
| 마지막 확인 | 2026-08-04 |

## 필드 ID

| 필드 | 타입 | Field ID |
| --- | --- | --- |
| Status | SingleSelect | `PVTSSF_lADOBD3v1M4BfKwozhZfjUA` |
| Priority | SingleSelect | `PVTSSF_lADOBD3v1M4BfKwozhZfjZI` |
| Work type | SingleSelect | `PVTSSF_lADOBD3v1M4BfKwozhZfmJA` |
| Sprint | Iteration | `PVTIF_lADOBD3v1M4BfKwozhZfjeg` |

## 옵션 ID

**Status** — Todo `f75ad846` / In Progress `47fc9ee4` / Done `98236657`

**Priority** — P0 `20ea1bf7` / P1 `8a5c5c84` / P2 `21f3e0e1`

우선순위 판단 기준을 질문 옵션 설명에 함께 보여준다.

- `P0` — 출시·후속 작업이 막히거나 운영 장애·데이터 정합성에 직접 영향
- `P1` — 이번 스프린트 목표에 필요한 기본 작업
- `P2` — 있으면 좋지만 스프린트를 넘겨도 되는 작업

**Work type** — Feature `0c8856c5` / Bug `68cbc789` / Refactor `60ed448b` /
Test `e33245d3` / Docs `55df97d3` / Infrastructure `30307899` /
Performance `03244399` / Chore `e8d1f607`

**Sprint (iteration, 7일 주기 · 월요일 시작)**

| Iteration ID | 제목 | 시작일 |
| --- | --- | --- |
| `30f27877` | Week 5 · Foundation | 2026-08-03 |
| `8dd52838` | Week 6 · Core creation | 2026-08-10 |
| `91e8a5ea` | Week 7 · Interaction | 2026-08-17 |
| `e5d52e2c` | Week 8 · Stabilization | 2026-08-24 |

## 최신 값 조회

iteration은 시간이 지나면 `completedIterations`로 이동하고 새 iteration이 생긴다.
캐시된 ID로 `item-edit`이 실패하거나 오늘 날짜가 위 표의 마지막 iteration을 지났으면
아래로 다시 읽고 이 문서를 갱신한다.

```bash
gh project field-list 141 --owner dnd-side-project --format json
```

```bash
gh api graphql -f query='
query {
  organization(login: "dnd-side-project") {
    projectV2(number: 141) {
      id
      field(name: "Sprint") {
        ... on ProjectV2IterationField {
          id
          configuration {
            iterations { id title startDate duration }
            completedIterations { id title startDate duration }
          }
        }
      }
    }
  }
}'
```

## 주의

- 스프린트·우선순위·상태는 GitHub 라벨로 복제하지 않는다(`docs/harness/LABELS.md`).
- `gh project` 명령에는 `project` 스코프가 필요하다. 권한 오류가 나면 사용자에게
  `! gh auth refresh -s project` 실행을 요청한다.
- 다른 사람이 이미 설정한 필드 값을 확인 없이 덮어쓰지 않는다.
