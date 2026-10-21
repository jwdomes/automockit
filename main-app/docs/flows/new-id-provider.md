# new-id-provider

New ID provider switched on: the person id comes from the new provider; no identifiers, so no partner is called

**Status:** 200 (expected 200) · **Called:** New ID Provider

## Request

<details><summary>POST /search</summary>

```json
{
  "header" : {
    "requestId" : "new-id-provider",
    "source" : "flow-scenario",
    "timestamp" : "2026-10-20T12:00:00Z"
  },
  "documents" : [ ],
  "person" : {
    "first" : "Mo",
    "last" : "Grant",
    "birthDate" : "1988-08-08"
  }
}
```

</details>

## Decisions

| Decision | Outcome | Conditions |
| --- | --- | --- |
| Use new ID provider? | yes | new provider enabled ✓ |
| Partner A eligible? | no | SSN present ✗, valid passport ✗ |
| Partner B eligible? | no | PNumber present ✗, valid driver license ✗ |
| Partner C eligible? | no | SSN present ✗ |

## Flow

```mermaid
sequenceDiagram
    %% New ID provider switched on: the person id comes from the new provider; no identifiers, so no partner is called
    participant UPSTREAM as Upstream
    participant ROUTER as Router
    participant NEW_ID_PROVIDER as New ID Provider
    UPSTREAM->>ROUTER: Receive search request
    ROUTER->>ROUTER: Determine downstream partners
    Note over ROUTER: Use new ID provider? yes — new provider enabled ✓
    ROUTER->>NEW_ID_PROVIDER: Call New ID Provider
    NEW_ID_PROVIDER-->>ROUTER: response
    Note over ROUTER: Partner A eligible? no — SSN present ✗, valid passport ✗
    Note over ROUTER: Partner B eligible? no — PNumber present ✗, valid driver license ✗
    Note over ROUTER: Partner C eligible? no — SSN present ✗
    ROUTER-->>UPSTREAM: 200 OK
```

## Downstream calls

### POST /new-id-provider/lookup → 200

Matched `stubs[0]`.

<details><summary>Request sent</summary>

```json
{
  "firstName" : "Mo",
  "lastName" : "Grant",
  "birthDate" : "1988-08-08",
  "ssn" : null,
  "pNumber" : null
}
```

</details>

<details><summary>Response returned</summary>

```json
{
  "personId" : "NEW-GRANT"
}
```

</details>

## Response

<details><summary>200 OK</summary>

```json
{
  "requestId" : "new-id-provider",
  "personId" : "NEW-GRANT",
  "results" : [ ]
}
```

</details>
