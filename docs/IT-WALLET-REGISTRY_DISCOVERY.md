# IT-Wallet Registry Discovery

Describes the federation registry resolution chain executed by the SDK at initialization time (`RegistryResolver`). All steps run online; failure in steps 1–4 aborts the chain and falls back to cached or built-in data. Steps 4', 5, and 6 degrade gracefully on failure.

JWT/JWS payloads are decoded without signature verification (Phase 1 scope).

---

## Step 1 — RP Entity Configuration

**URL:** `{federationEntityUrl}/.well-known/openid-federation`

Fetches the Relying Party's own OpenID Federation Entity Configuration (compact JWS). Extracts `authority_hints[0]` to obtain the Trust Anchor base URL.

---

## Step 2 — Trust Anchor Entity Configuration

**URL:** `{trustAnchorUrl}/.well-known/openid-federation`

Fetches the Trust Anchor Entity Configuration (compact JWS). Extracts `metadata.federation_entity.federation_resolve_endpoint` to obtain the resolve endpoint used in Step 5.

---

## Step 3 — IT-Wallet Registry Discovery

**URL:** `{trustAnchorUrl}/.well-known/it-wallet-registry`

Fetches the IT-Wallet registry discovery document (JSON or compact JWS). Extracts:

| Field | Usage |
|---|---|
| `credential_catalog_endpoint` (or `endpoints.credential_catalog`) | Step 4 |
| `schema_registry_endpoint` (or `endpoints.schema_registry`) | Step 4' (optional) |
| `claims_registry_endpoint` (or `endpoints.claims_registry`) | Step 6 (optional) |

Missing `credential_catalog_endpoint` aborts the chain.

---

## Step 4 — Digital Credentials Catalog

**URL:** `{credentialCatalogEndpoint}`

Accepts `application/json`, `application/jwt`, `application/jose`. Fetches the catalog (§7.6.2) and parses each entry in `credentials[]`:

- `credential_type` — opaque credential identifier
- `issuers[].id` (or `issuers[].entity_id`) — authorized issuer entity IDs
- `version` — optional, used for diagnostics
- `restriction_policy.presentation_flows.proximity` — if explicitly `false`, the entry is excluded

Entries are filtered by `allowedIssuerIds` when provided.

---

## Step 4' — Schema Registry _(optional)_

**URL:** `{schemaRegistryEndpoint}`

Fetches the schema registry and parses `schemas[]`. Only entries with `format == "mso_mdoc"` are retained. Builds a `credentialType → docType` map used in Step 5 to restrict which issuer configurations are registered.

If the endpoint is absent or unreachable, Step 5 falls back to accepting all `mso_mdoc` configurations from discovered issuers (degraded mode).

---

## Step 5 — Issuer Metadata Resolve _(per eligible issuer)_

**URL (first attempt):** `{resolveEndpoint}?sub={issuerEntityId}&trust_anchor={trustAnchorUrl}&entity_type=openid_credential_issuer`

**URL (fallback):** `{resolveEndpoint}?sub={issuerEntityId}&trust_anchor={trustAnchorUrl}`

Accepts `application/json`, `application/resolve-response+jwt`. Executed for each unique issuer ID collected in Step 4. Parses `metadata.openid_credential_issuer.credential_configurations_supported`, keeping only `mso_mdoc` entries. If Step 4' provided a docType map, only entries whose `doctype` is in that map are kept.

Also extracts `status_list_aggregation_endpoint` when present (persisted, not consumed in Phase 1).

Failure for one issuer skips only that issuer; resolution continues for the others.

---

## Step 6 — Claims Registry _(optional)_

**URL:** `{claimsRegistryEndpoint}`

Accepts `application/json`, `application/jwt`. Parses `claims{}` into a `claimName → {type, format, aliases}` map. Used to enrich `ClaimDescriptor.dataType` for claims whose type is not explicitly set by the issuer metadata.

Lookup order per claim:
1. Canonical name match (`claimName == claim.identifier`)
2. Alias match (`aliases` array)
3. `KNOWN_ELEMENT_TYPES` fallback (ISO 18013-5 well-known elements)
4. `string` fallback

If the endpoint is absent or unreachable, credentials are registered without enrichment.

---

## Resilience summary

| Step | Failure behaviour |
|---|---|
| 1–4 | Abort online chain → use cached data or built-in catalog |
| 4' (Schema Registry) | Skip docType filter → accept all issuer `mso_mdoc` configs |
| 5 (single issuer) | Skip that issuer, continue with the rest |
| 6 (Claims Registry) | Register credentials without claim type enrichment |
