# GH-223 lite runtime evidence adapter

## Scope

This adapter records the runtime evidence that Codex CLI 0.153.4 serializes for the approved lite comparison. It does not claim to validate the complete Codex environment or complete built-in, MCP, app, or plugin tool catalogs.

## Exact-version serialization evidence

In the exact `rust-v0.153.4` source:

- [`SessionMeta.dynamic_tools`](https://github.com/openai/codex/blob/rust-v0.153.4/codex-rs/protocol/src/protocol.rs) is `Option<Vec<DynamicToolSpec>>`, defaults when missing, and is omitted when `None`.
- The [rollout recorder constructor](https://github.com/openai/codex/blob/rust-v0.153.4/codex-rs/rollout/src/recorder.rs) sets `dynamic_tools` to `None` exactly when the supplied dynamic-tools vector is empty.
- The pinned [`DynamicToolSpec` schema](https://github.com/openai/codex/blob/rust-v0.153.4/codex-rs/protocol/src/dynamic_tools.rs) tags each entry as `function` or `namespace`. Functions require string `name` and `description` fields plus a JSON-valued `inputSchema`; optional `deferLoading` must be boolean. Namespaces require string `name` and `description` fields and a `tools` list containing canonical tagged functions.

For a newly recorded CLI 0.153.4 session, an absent `session_meta.dynamic_tools` field therefore means the dynamic supplement was empty. This interpretation is version-specific. It does not mean that the complete tool catalog was empty.

## Evidence fields

The lite fingerprint preserves:

- the base-instructions hash;
- resolved model and reasoning effort;
- CLI version;
- the normalized dynamic-supplement hash and its type/name/description projection hash;
- explicit source, serialization, and scope markers.

An omitted field is normalized to `[]` only for CLI 0.153.4 and is labeled `empty_omitted_for_cli_0.153.4`. A non-empty serialized list is labeled `explicit_list` only after every function, namespace, and nested namespace function matches that canonical tagged schema. Explicit `null`, explicit empty lists, malformed entries, unknown-version omission, and missing required base/model/effort/CLI evidence are rejected.

The fingerprint states:

```text
dynamic_tools_scope: dynamic_supplement_only
full_tool_catalog_status: unavailable_from_serialized_rollout
plugin_catalog_status: unavailable_from_serialized_rollout
```

No full-catalog hash is synthesized from the optional dynamic supplement.

## Comparison gate

The approved pilot pair must have identical scoped parent fingerprints. Child fingerprints are validated with the same lite schema and are frozen by child role. Every later comparison run must match the promoted fingerprint, including the provenance and scope markers.

The original failed B0/L1 pilot remains diagnostic evidence only. Its invalid summary and empty runtime-evidence artifact cannot be promoted. A new verified B0/L1 and B3/L2 pilot pair is required before strict verification or any comparison run can proceed.

Complete tool/plugin catalog comparability remains unavailable and must be reported as a limitation. This adapter does not convert that unavailable evidence into a complete-environment validation claim.
