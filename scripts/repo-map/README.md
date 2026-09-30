# Mechanical Java Repo Map

Requires Git, Python 3.9+ and **JDK 21** (`JAVA_HOME` selects Java; otherwise PATH).
No dependencies are downloaded and no model/service is called.

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21) # macOS example
python3 scripts/repo-map/run.py generate --root .
python3 scripts/repo-map/run.py check --root .
python3 scripts/repo-map/run.py query --root . --symbol ProfileService --limit 10
python3 scripts/repo-map/run.py query --root . --format classes --symbol ProfileService --limit 5
```

All commands accept `--output build/repo-map/index.json` (the default). Relative
output paths are relative to `--root`, which must be the Git repository root.
Outputs must be untracked, Git-ignored `.json` files under `build/repo-map/`.
Source/output/root symlinks are rejected, including symlinks within source trees.

The index is one compact JSON object, not JSONL. Schema 2 adds type metadata;
old schema 1 maps must be regenerated. `schema`, `tool` (SHA-256 of
both implementation files), `runtime` (Java and Python versions), `head` and `sources`
(repository-relative path → content SHA-256) identify its inputs. `rows` is
sorted by file, line, kind, type and signature. Generation uses tracked files
and nonignored untracked Java files under `src/main/java`, `src/test/java` and
`src/integrationTest/java`. Deleted files disappear from the snapshot. Ignored
new/generated files and other source roots are excluded.

File rows contain package/import syntax. Type rows contain qualified nested
names, type parameters and syntactic extends/implements/permits. Method rows
contain owner, name, parameter types, return type and throws syntax. All rows
have repository-relative `file`, 1-based `line`, `kind`, `symbol`, `type` and
`signature`, plus `source_root` and `source_path` relative within that root.
Type annotations and their values are stripped. Field/record-component type
references are metadata on the owning type row; they do not add rows. Implicit
constructors/accessors, local/anonymous classes, method bodies, initializers,
annotation values, comments and literal values are not indexed. Explicit
constructors remain method rows. Varargs use the AST array representation.

`JavacTask.parse()` with `-proc:none --release 21` performs **syntax only**:
no annotation processing, type resolution, dependency loading, generated code,
call graph or behavioral summary. Missing symbols and semantic type errors do
not prevent indexing; parse errors do. Unknown type syntax fails closed.

`check` and `query` reject changed source lists/content, HEAD, tool, schema or
Java/Python runtime. The default `--format rows` searches symbol/type/signature/file case-insensitively; result
JSON includes total matches, `truncated`, and at most `--limit` rows (1–200).
No match is an empty array with total zero. This map gives navigation evidence,
not proof that a declaration is semantically resolved or behavior is correct.

Generation compares snapshots before and after parsing, then publishes through
an atomic same-directory replacement. Failed parsing leaves an old index
untouched. Error output withholds compiler/source diagnostics. This is local
trusted-workspace tooling, not an adversarial filesystem sandbox: concurrent
writers and changes in the small interval after the final snapshot are not
locked out. Run check again after edits and avoid simultaneous generation.
Generated maps contain code identifiers and remain local ignored artifacts;
do not include them in measurement records.

## Class groups

`query --format classes` returns `{total, truncated, classes: [...]}`. Each group
contains `file` (repository-relative), `path` (source-root-relative),
`source_root`, fully qualified nested `type`, `kind`, `line`, `symbols`,
`dependencies`, and `dependency_evidence`. The list retains declarations from
different files/source roots even when fully qualified names coincide. Groups
sort by file, type and line. `--symbol` matches class name, file path, declared
method name or dependency case-insensitively; `--limit` counts complete matching
groups, so methods/dependencies inside a returned group are never silently cut.
The limit bounds group count, not the byte size of an unusually large class.

`symbols` contains sorted, unique directly declared method names; overloads
collapse to one name. Constructors are excluded from these names. `dependencies`
is a sorted unique set of named type references written in direct fields and
explicit constructor parameters. Every reference has evidence shaped as
`{dependency, source, name, line}`, where source is `field` or
`constructor_parameter`, name identifies that declaration and line is its
1-based starting line (including attached annotations). Repeated evidence is
deduplicated and sorted by dependency, source, name and line.

Static fields are included. Record header components are represented by javac
as field declarations and use `field` evidence; compact constructor implicit
parameters add no evidence. Enum constants' implicit self types are excluded,
while explicitly typed enum fields are included. Nested type fields belong only
to their own group. Generic/array/wildcard types contribute named raw types and
named arguments/bounds, excluding primitives and in-scope generic parameter
names. Qualified names retain their written qualification; imports are not
resolved. Generic owner syntax such as `Outer<Value>.Inner<Port>` contributes
`Outer.Inner`, `Value` and `Port`.

These are declared type references, including value types such as `String`.
They do not establish dependency injection, Spring bindings, runtime calls,
instantiation, generated Lombok constructors or resolved semantic dependencies.
Method parameter/return types, inheritance and generic declaration bounds remain
available in regular signatures but are not dependency evidence in this view.

Optional [B search guidance](../../docs/harness/REPO_MAP_SEARCH.md) is available
for explicitly selected tasks or sessions; ordinary map use does not activate it.
