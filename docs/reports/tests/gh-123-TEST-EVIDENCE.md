# Issue #123 Test Evidence

Captured at: `2026-08-14T18:29:00+09:00`

Branch: `feat/gh-123-direction-notification-fanout`
Base commit: `a8e307d`

The targeted Gradle commands below completed with exit status `0`. The XML paths
are generated locally by Gradle and contain the durable test counts for this
verification run. A later `harness test-run` rerun also completed its unit and
integration Gradle tasks successfully, but the wrapper returned exit `2` because
it refuses to overwrite the already completed report; this is a report-scaffold
guard, not a test failure.

## Targeted unit tests

```text
./gradlew test --tests "com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorkerTest" --tests "com.dnd.qello.direction.matching.DirectionMatchingWorkerTest" --max-workers=1 --no-daemon --rerun-tasks
```

Result: `BUILD SUCCESSFUL in 7s`; 37 tests, 0 failures, 0 errors, 0 skipped.

- `build/test-results/test/TEST-com.dnd.qello.notification.fanout.RecipientNotificationFanOutWorkerTest.xml` — 29/0/0/0
- `build/test-results/test/TEST-com.dnd.qello.direction.matching.DirectionMatchingWorkerTest.xml` — 8/0/0/0

## Related integration and concurrency regression

```text
./gradlew integrationTest --tests "com.dnd.qello.NotificationFanOutPersistenceIntegrationTest" --tests "com.dnd.qello.RecipientNotificationFanOutWorkerIntegrationTest" --tests "com.dnd.qello.RecipientNotificationFanOutWorkerConcurrencyIntegrationTest" --tests "com.dnd.qello.OutboxLeaseIntegrationTest" --tests "com.dnd.qello.DirectionMatchingWorkerIntegrationTest" --tests "com.dnd.qello.DirectionMatchingWorkerConcurrencyIntegrationTest" --tests "com.dnd.qello.AnswerSafetyNotificationPersistenceIntegrationTest" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
```

Follow-up result after lease-fencing remediation: `BUILD SUCCESSFUL in 41s`;
93 tests, 0 failures, 0 errors, 0 skipped.

- `build/test-results/integrationTest/TEST-com.dnd.qello.NotificationFanOutPersistenceIntegrationTest.xml` — 5/0/0/0
- `build/test-results/integrationTest/TEST-com.dnd.qello.RecipientNotificationFanOutWorkerIntegrationTest.xml` — 40/0/0/0
- `build/test-results/integrationTest/TEST-com.dnd.qello.RecipientNotificationFanOutWorkerConcurrencyIntegrationTest.xml` — 7/0/0/0
- `build/test-results/integrationTest/TEST-com.dnd.qello.OutboxLeaseIntegrationTest.xml` — 9/0/0/0
- `build/test-results/integrationTest/TEST-com.dnd.qello.DirectionMatchingWorkerIntegrationTest.xml` — 17/0/0/0
- `build/test-results/integrationTest/TEST-com.dnd.qello.DirectionMatchingWorkerConcurrencyIntegrationTest.xml` — 3/0/0/0
- `build/test-results/integrationTest/TEST-com.dnd.qello.AnswerSafetyNotificationPersistenceIntegrationTest.xml` — 12/0/0/0

## Harness and repository gates

```text
./harness test-run --id TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

The last clean, pre-remediation harness run completed successfully and reported
699 tests before the additional lease-fencing unit case. The later rerun's current XML aggregate
is 700 tests, with no failures, errors, or skips; its underlying Gradle tasks
also passed; only report scaffolding was refused because the report already
exists. `harness check`, `pr-ready`, hooks validation, and `git diff --check`
passed after remediation. Remote CI is not available until a PR is created.

Environment note: PostgreSQL/PostGIS Testcontainers used a local amd64 image
under arm64 Docker emulation; slower CI hosts may need timeout headroom.
