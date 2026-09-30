# Optional B repository search guidance

B must be selected explicitly for the current task or session. Merging these tools
or linking this document does not activate B or change common repository policies.
Preserve all mandatory repository instructions, including the Issue/branch/TASK
implementation gates. Search guidance grants no implementation authorization.

This repository provides Repo Map generation, freshness checks, queries and this
reusable search procedure. Experiment task registration, A/B assignment, session
linkage and measurement belong to harness-delta. Product execution and version
policy are outside the backend's responsibility.

## Search procedure

1. Use `rg`, file and symbol search to narrow candidates before reading the
   necessary sources and authoritative documents.
2. Use an existing LSP when useful; do not install one for this procedure.
3. Widen searches as needed. There is no reading quota.
4. Use bounded map queries as navigation evidence. Never inject the full map.
5. Verify relevant source evidence before drawing conclusions. The map is
   syntax-only, not a resolved dependency or call graph.

## Local map use

Run the following from the target repository root with JDK 21 and Python 3.9+.
The [tool reference](../../scripts/repo-map/README.md) describes output constraints,
query formats and indexing limits. Generation and queries make no model calls.

```sh
python3 scripts/repo-map/run.py generate --root .
python3 scripts/repo-map/run.py check --root .
python3 scripts/repo-map/run.py query --root . --symbol 'SYMBOL' --limit 20
```

Replace `SYMBOL` with the search term. The default index is the local ignored
`build/repo-map/index.json`. Check freshness before use; regenerate after changes
to indexed sources, HEAD, tools or the Java/Python runtime. Queries also reject
stale indexes. Keep generated indexes local and out of measurement records.

For grouped class symbols and declared field/explicit-constructor type references:

```sh
python3 scripts/repo-map/run.py query --root . --symbol 'SYMBOL' --format classes --limit 5
```

Declared references do not resolve imports, runtime injection or method calls.
The class limit bounds group count, not the size of every group's contents.

## Historical evidence

Existing test plans/reports and Git history retain the removed experimental
runner's original behavior and checks; they are not current runner instructions.
Existing private local experiment records remain outside this repository.
A prior manual Docker check completed four exploration sessions with partial
usage collection. Human answer-quality assessment remains incomplete, and that
operational observation does not establish B superiority or justify adoption.
