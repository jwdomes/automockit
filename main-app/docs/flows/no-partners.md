# no-partners

No SSN and no PNumber: no partner is called

**Status:** 200 (expected 200) · **Called:** Legacy ID Provider

## Request

<details><summary>POST /search</summary>

```json
{
  "header" : {
    "requestId" : "no-partners",
    "source" : "flow-scenario",
    "timestamp" : "2026-10-20T12:00:00Z"
  },
  "documents" : [ ],
  "person" : {
    "first" : "Jo",
    "last" : "Ellis",
    "birthDate" : "2000-01-01"
  }
}
```

</details>

## Decisions

| Decision | Outcome | Conditions |
| --- | --- | --- |
| Use new ID provider? | no | new provider enabled ✗ |
| Partner A eligible? | no | SSN present ✗, valid passport ✗ |
| Partner B eligible? | no | PNumber present ✗, valid driver license ✗ |
| Partner C eligible? | no | SSN present ✗ |

## Flow

```mermaid
sequenceDiagram
    %% No SSN and no PNumber: no partner is called
    participant UPSTREAM as Upstream
    participant ROUTER as Router
    participant LEGACY_ID_PROVIDER as Legacy ID Provider
    UPSTREAM->>ROUTER: Receive search request
    ROUTER->>ROUTER: Determine downstream partners
    Note over ROUTER: Use new ID provider? no — new provider enabled ✗
    ROUTER->>LEGACY_ID_PROVIDER: Call Legacy ID Provider
    LEGACY_ID_PROVIDER-->>ROUTER: response
    Note over ROUTER: Partner A eligible? no — SSN present ✗, valid passport ✗
    Note over ROUTER: Partner B eligible? no — PNumber present ✗, valid driver license ✗
    Note over ROUTER: Partner C eligible? no — SSN present ✗
    ROUTER-->>UPSTREAM: 200 OK
```

## Downstream calls

### POST /legacy-id-provider/lookup → 200

Matched `stubs[0]`.

<details><summary>Request sent</summary>

```json
{
  "firstName" : "Jo",
  "lastName" : "Ellis",
  "birthDate" : "2000-01-01",
  "ssn" : null,
  "pNumber" : null
}
```

</details>

<details><summary>Response returned</summary>

```json
{
  "personId" : "LEGACY-ELLIS"
}
```

</details>

## Response

<details><summary>200 OK</summary>

```json
{
  "requestId" : "no-partners",
  "personId" : "LEGACY-ELLIS",
  "results" : [ ]
}
```

</details>
