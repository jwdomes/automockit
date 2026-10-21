# a-and-c

SSN and valid passport but no PNumber: A and C are called, B is skipped

**Status:** 200 (expected 200) · **Called:** Legacy ID Provider, Partner A, Partner C

## Request

<details><summary>POST /search</summary>

```json
{
  "header" : {
    "requestId" : "a-and-c",
    "source" : "flow-scenario",
    "timestamp" : "2026-10-20T12:00:00Z"
  },
  "documents" : [ {
    "documentType" : "PASSPORT",
    "documentNumber" : "FA0000002"
  }, {
    "documentType" : "DRIVER_LICENSE",
    "documentNumber" : "F00000002"
  } ],
  "person" : {
    "first" : "Gil",
    "last" : "Baker",
    "ssn" : "900000002",
    "birthDate" : "1975-05-05"
  }
}
```

</details>

## Decisions

| Decision | Outcome | Conditions |
| --- | --- | --- |
| Use new ID provider? | no | new provider enabled ✗ |
| Partner A eligible? | yes | SSN present ✓, valid passport ✓ |
| Partner B eligible? | no | PNumber present ✗, valid driver license ✓ |
| Partner C eligible? | yes | SSN present ✓ |

## Flow

```mermaid
sequenceDiagram
    %% SSN and valid passport but no PNumber: A and C are called, B is skipped
    participant UPSTREAM as Upstream
    participant ROUTER as Router
    participant LEGACY_ID_PROVIDER as Legacy ID Provider
    participant PARTNER_A as Partner A
    participant PARTNER_C as Partner C
    UPSTREAM->>ROUTER: Receive search request
    ROUTER->>ROUTER: Determine downstream partners
    Note over ROUTER: Use new ID provider? no — new provider enabled ✗
    ROUTER->>LEGACY_ID_PROVIDER: Call Legacy ID Provider
    LEGACY_ID_PROVIDER-->>ROUTER: response
    Note over ROUTER: Partner A eligible? yes — SSN present ✓, valid passport ✓
    Note over ROUTER,PARTNER_A: Using passport FA0000002
    ROUTER->>PARTNER_A: Call Partner A
    PARTNER_A-->>ROUTER: response
    Note over ROUTER: Partner B eligible? no — PNumber present ✗, valid driver license ✓
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
  "firstName" : "Gil",
  "lastName" : "Baker",
  "birthDate" : "1975-05-05",
  "ssn" : "900000002",
  "pNumber" : null
}
```

</details>

<details><summary>Response returned</summary>

```json
{
  "personId" : "LEGACY-BAKER"
}
```

</details>

### POST /partner-a/matches → 200

Matched `stubs[1]`.

<details><summary>Request sent</summary>

```json
{
  "firstName" : "Gil",
  "lastName" : "Baker",
  "documentNumber" : "FA0000002",
  "personId" : "900000002"
}
```

</details>

<details><summary>Response returned</summary>

```json
{
  "matches" : [ {
    "firstName" : "Gil",
    "lastName" : "Baker",
    "birthDate" : "1975-05-05",
    "matchProbability" : 1.0
  } ]
}
```

</details>

### POST /partner-c/matches → 200

Matched `stubs[2]`.

<details><summary>Request sent</summary>

```json
{
  "lastName" : "Baker",
  "birthDate" : "1975-05-05",
  "personIdentifier" : "900000002"
}
```

</details>

<details><summary>Response returned</summary>

```json
{
  "matches" : [ {
    "firstName" : "Gil",
    "lastName" : "Baker",
    "birthDate" : "1975-05-05",
    "matchProbability" : 1.0
  } ]
}
```

</details>

## Response

<details><summary>200 OK</summary>

```json
{
  "requestId" : "a-and-c",
  "personId" : "LEGACY-BAKER",
  "results" : [ {
    "partner" : "PARTNER_A",
    "matches" : [ {
      "firstName" : "Gil",
      "lastName" : "Baker",
      "birthDate" : "1975-05-05",
      "matchProbability" : 1.0
    } ]
  }, {
    "partner" : "PARTNER_C",
    "matches" : [ {
      "firstName" : "Gil",
      "lastName" : "Baker",
      "birthDate" : "1975-05-05",
      "matchProbability" : 1.0
    } ]
  } ]
}
```

</details>
