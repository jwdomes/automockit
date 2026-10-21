# no-partners

No SSN and no PNumber: no partner is called

**Status:** 200 (expected 200) · **Called:** none

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
| Partner A eligible? | no | SSN present ✗, valid passport ✗ |
| Partner B eligible? | no | PNumber present ✗, valid driver license ✗ |
| Partner C eligible? | no | SSN present ✗ |

## Flow

```mermaid
sequenceDiagram
    %% No SSN and no PNumber: no partner is called
    participant UPSTREAM as Upstream
    participant ROUTER as Router
    UPSTREAM->>ROUTER: Receive search request
    ROUTER->>ROUTER: Determine downstream partners
    Note over ROUTER: Partner A eligible? no — SSN present ✗, valid passport ✗
    Note over ROUTER: Partner B eligible? no — PNumber present ✗, valid driver license ✗
    Note over ROUTER: Partner C eligible? no — SSN present ✗
    ROUTER-->>UPSTREAM: 200 OK
```

## Downstream calls

None.

## Response

<details><summary>200 OK</summary>

```json
{
  "requestId" : "no-partners",
  "results" : [ ]
}
```

</details>
