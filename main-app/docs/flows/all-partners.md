# all-partners

SSN, PNumber, valid passport and license: A, B and C are all called

**Status:** 200 (expected 200) · **Partners called:** Partner A, Partner B, Partner C

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

| Decision | Outcome | Reason |
| --- | --- | --- |
| Partner A eligible? | yes | SSN + valid passport |
| Partner B eligible? | yes | PNumber + valid driver license |
| Partner C eligible? | yes | SSN |

## Flow

```mermaid
sequenceDiagram
    %% SSN, PNumber, valid passport and license: A, B and C are all called
    participant UPSTREAM as Upstream
    participant ROUTER as Router
    participant PARTNER_A as Partner A
    participant PARTNER_B as Partner B
    participant PARTNER_C as Partner C
    UPSTREAM->>ROUTER: Receive search request
    ROUTER->>ROUTER: Validate request
    ROUTER->>ROUTER: Determine downstream partners
    Note over ROUTER: Partner A eligible? yes — SSN + valid passport
    Note over ROUTER: Using passport FA0000001
    ROUTER->>PARTNER_A: Call Partner A
    PARTNER_A-->>ROUTER: response
    Note over ROUTER: Partner B eligible? yes — PNumber + valid driver license
    Note over ROUTER: Using driver license F00000001
    ROUTER->>PARTNER_B: Call Partner B
    PARTNER_B-->>ROUTER: response
    Note over ROUTER: Partner C eligible? yes — SSN
    ROUTER->>PARTNER_C: Call Partner C
    PARTNER_C-->>ROUTER: response
    ROUTER-->>UPSTREAM: 200 OK
```

## Partner calls

### Partner A: POST /partner-a/matches → 200

Matched `stubs[0]`.

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

### Partner B: POST /partner-b/matches → 200

Matched `stubs[1]`.

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

### Partner C: POST /partner-c/matches → 200

Matched `stubs[2]`.

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

## Router response

<details><summary>200 OK</summary>

```json
{
  "requestId" : "all-partners",
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
