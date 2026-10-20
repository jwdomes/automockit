# no-partners

No SSN and no PNumber: no partner is called

**Status:** 200 (expected 200) · **Partners called:** none

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

| Decision | Outcome | Reason |
| --- | --- | --- |
| Partner A eligible? | no | no SSN |
| Partner B eligible? | no | no PNumber |
| Partner C eligible? | no | no SSN |

## Flow

```mermaid
sequenceDiagram
    %% No SSN and no PNumber: no partner is called
    participant UPSTREAM as Upstream
    participant ROUTER as Router
    UPSTREAM->>ROUTER: Receive search request
    ROUTER->>ROUTER: Validate request
    ROUTER->>ROUTER: Determine downstream partners
    Note over ROUTER: Partner A eligible? no — no SSN
    Note over ROUTER: Partner B eligible? no — no PNumber
    Note over ROUTER: Partner C eligible? no — no SSN
    ROUTER-->>UPSTREAM: 200 OK
```

## Partner calls

None.

## Router response

<details><summary>200 OK</summary>

```json
{
  "requestId" : "no-partners",
  "results" : [ ]
}
```

</details>
