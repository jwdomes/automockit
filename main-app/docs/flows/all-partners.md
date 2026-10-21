# all-partners

SSN, PNumber, valid passport and license: A, B and C are all called

**Status:** 200 (expected 200) · **Called:** Legacy ID Provider, Partner A, Partner B, Partner C

## Request

<details><summary>POST /search</summary>

```json
{
  "header" : {
    "requestId" : "all-partners",
    "source" : "flow-scenario",
    "timestamp" : "2026-10-20T12:00:00Z"
  },
  "documents" : [ {
    "documentType" : "PASSPORT",
    "documentNumber" : "FA0000001"
  }, {
    "documentType" : "DRIVER_LICENSE",
    "documentNumber" : "F00000001"
  } ],
  "person" : {
    "first" : "Fiona",
    "last" : "Ames",
    "ssn" : "900000001",
    "pNumber" : "PN900001",
    "birthDate" : "1980-01-01"
  }
}
```

</details>

## Decisions

| Decision | Outcome | Conditions |
| --- | --- | --- |
| Partner A eligible? | yes | SSN present ✓, valid passport ✓ |
| Partner B eligible? | yes | PNumber present ✓, valid driver license ✓ |
| Partner C eligible? | yes | SSN present ✓ |

## Flow

```mermaid
sequenceDiagram
    %% SSN, PNumber, valid passport and license: A, B and C are all called
    participant UPSTREAM as Upstream
    participant ROUTER as Router
    participant LEGACY_ID_PROVIDER as Legacy ID Provider
    participant PARTNER_A as Partner A
    participant PARTNER_B as Partner B
    participant PARTNER_C as Partner C
    UPSTREAM->>ROUTER: Receive search request
    ROUTER->>ROUTER: Determine downstream partners
    ROUTER->>LEGACY_ID_PROVIDER: Call Legacy ID Provider
    LEGACY_ID_PROVIDER-->>ROUTER: response
    Note over ROUTER: Partner A eligible? yes — SSN present ✓, valid passport ✓
    Note over ROUTER,PARTNER_A: Using passport FA0000001
    ROUTER->>PARTNER_A: Call Partner A
    PARTNER_A-->>ROUTER: response
    Note over ROUTER: Partner B eligible? yes — PNumber present ✓, valid driver license ✓
    Note over ROUTER,PARTNER_B: Using driver license F00000001
    ROUTER->>PARTNER_B: Call Partner B
    PARTNER_B-->>ROUTER: response
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
  "firstName" : "Fiona",
  "lastName" : "Ames",
  "birthDate" : "1980-01-01",
  "ssn" : "900000001",
  "pNumber" : "PN900001"
}
```

</details>

<details><summary>Response returned</summary>

```json
{
  "personId" : "LEGACY-AMES"
}
```

</details>

### POST /partner-a/matches → 200

Matched `stubs[1]`.

<details><summary>Request sent</summary>

```json
{
  "firstName" : "Fiona",
  "lastName" : "Ames",
  "documentNumber" : "FA0000001",
  "personId" : "900000001"
}
```

</details>

<details><summary>Response returned</summary>

```json
{
  "matches" : [ {
    "firstName" : "Fiona",
    "lastName" : "Ames",
    "birthDate" : "1980-01-01",
    "matchProbability" : 1.0
  } ]
}
```

</details>

### POST /partner-b/matches → 200

Matched `stubs[2]`.

<details><summary>Request sent</summary>

```json
{
  "firstName" : "Fiona",
  "lastName" : "Ames",
  "birthDate" : "1980-01-01",
  "documentNumber" : "F00000001",
  "pNumber" : "PN900001"
}
```

</details>

<details><summary>Response returned</summary>

```json
{
  "matches" : [ {
    "firstName" : "Fiona",
    "lastName" : "Ames",
    "birthDate" : "1980-01-01",
    "matchProbability" : 1.0
  }, {
    "firstName" : "Fiona",
    "lastName" : "Amos",
    "birthDate" : "1980-01-01",
    "matchProbability" : 0.7
  } ]
}
```

</details>

### POST /partner-c/matches → 200

Matched `stubs[3]`.

<details><summary>Request sent</summary>

```json
{
  "lastName" : "Ames",
  "birthDate" : "1980-01-01",
  "personIdentifier" : "900000001"
}
```

</details>

<details><summary>Response returned</summary>

```json
{
  "matches" : [ {
    "firstName" : "Fiona",
    "lastName" : "Ames",
    "birthDate" : "1980-01-01",
    "matchProbability" : 1.0
  } ]
}
```

</details>

## Response

<details><summary>200 OK</summary>

```json
{
  "requestId" : "all-partners",
  "personId" : "LEGACY-AMES",
  "results" : [ {
    "partner" : "PARTNER_A",
    "matches" : [ {
      "firstName" : "Fiona",
      "lastName" : "Ames",
      "birthDate" : "1980-01-01",
      "matchProbability" : 1.0
    } ]
  }, {
    "partner" : "PARTNER_B",
    "matches" : [ {
      "firstName" : "Fiona",
      "lastName" : "Ames",
      "birthDate" : "1980-01-01",
      "matchProbability" : 1.0
    }, {
      "firstName" : "Fiona",
      "lastName" : "Amos",
      "birthDate" : "1980-01-01",
      "matchProbability" : 0.7
    } ]
  }, {
    "partner" : "PARTNER_C",
    "matches" : [ {
      "firstName" : "Fiona",
      "lastName" : "Ames",
      "birthDate" : "1980-01-01",
      "matchProbability" : 1.0
    } ]
  } ]
}
```

</details>
