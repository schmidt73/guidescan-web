# Job Status

Gets the status of a submitted query given its ID.

**URL**: `/job/status/:id{[0-9]+}`
**METHOD**: `GET`

## Success Response

**Condition**: Job is found.
**Code**: `200 OK`
**Content**:

```json
{"job-status": [STRING ["success" | "failure" | "pending"]]
 "failure":    [STRING Failure message] // OPTIONAL}
```

## Error Response

**Condition**: Job is not found.
**Code**: `404 Not Found`
