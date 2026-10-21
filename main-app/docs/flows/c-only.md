# c-only

SSN with a malformed passport: only C is called

**Status:** 200 (expected 200) · **Called:** Legacy ID Provider, Partner C

## Request

<details><summary>POST /search</summary>

```json
{
  "header" : {
    "requestId" : "c-only",
    "source" : "flow-scenario",
    "timestamp" : "2026-10-20T12:00:00Z"
  },
  "documents" : [ {
    "documentType" : "PASSPORT",
    "documentNumber" : "BAD"
  } ],
  "person" : {
    "first" : "Ivan",
    "last" : "Diaz",
    "ssn" : "900000004",
    "birthDate" : "1985-03-03"
  }
}
```

</details>

## Decisions

| Decision | Outcome | Conditions |
| --- | --- | --- |
| Use new ID provider? | no | new provider enabled ✗ |
| Partner A eligible? | no | SSN present ✓, valid passport ✗ |
| Partner B eligible? | no | PNumber present ✗, valid driver license ✗ |
| Partner C eligible? | yes | SSN present ✓ |

## Flow

```mermaid
sequenceDiagram
    %% SSN with a malformed passport: only C is called
    participant UPSTREAM as Upstream
    participant ROUTER as Router
    participant LEGACY_ID_PROVIDER as Legacy ID Provider
    participant PARTNER_C as Partner C
    UPSTREAM->>ROUTER: Receive search request
    ROUTER->>ROUTER: Determine downstream partners
    Note over ROUTER: Use new ID provider? no — new provider enabled ✗
    ROUTER->>LEGACY_ID_PROVIDER: Call Legacy ID Provider
    LEGACY_ID_PROVIDER-->>ROUTER: response
    Note over ROUTER: Partner A eligible? no — SSN present ✓, valid passport ✗
    Note over ROUTER: Partner B eligible? no — PNumber present ✗, valid driver license ✗
    Note over ROUTER: Partner C eligible? yes — SSN present ✓
    ROUTER->>PARTNER_C: Call Partner C
    PARTNER_C-->>ROUTER: response
    ROUTER-->>UPSTREAM: 200 OK
```

## Downstream calls

### POST /legacy-id-provider/lookup → 200

Matched `stubs[0]`.

<details><summary>Request sent</summary>

```json
{
  "firstName" : "Ivan",
  "lastName" : "Diaz",
  "birthDate" : "1985-03-03",
  "ssn" : "900000004",
  "pNumber" : null
}
```

</details>

<details><summary>Response returned</summary>

```json
{
  "personId" : "LEGACY-DIAZ"
}
```

</details>

### POST /partner-c/matches → 200

Matched `stubs[1]`.

<details><summary>Request sent</summary>

```json
{
  "lastName" : "Diaz",
  "birthDate" : "1985-03-03",
  "personIdentifier" : "900000004"
}
```

</details>

<details><summary>Response returned</summary>

```json
{
  "matches" : [ ]
}
```

</details>

## Response

<details><summary>200 OK</summary>

```json
{
  "requestId" : "c-only",
  "personId" : "LEGACY-DIAZ",
  "results" : [ {
    "partner" : "PARTNER_C",
    "matches" : [ ]
  } ]
}
```

</details>
