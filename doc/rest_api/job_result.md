# Job Result

Gets the result of a submitted query given its ID and the desired
format.

**URL**: `/job/result/:format{csv|json|bed}/:id{[0-9]+}`

**METHOD**: `GET`

## Success Response

**Condition**: Job was successfully completed.

**Code**: 200 OK

**Content**:

A response can come in either a JSON/BED/CSV format. We will
document the schema for a JSON response here.

Response Schema:

```
[[query-region-1 [gRNA-1-1, gRNA-1-2, ...]]
 [query-region-2 [gRNA-2-1, gRNA-2-2, ...]]
 ...
 [query-region-N [gRNA-N-1, gRNA-N-2, ...]]]
```

or equivalently,

```
[ARRAY [2-ARRAY [OBJECT query-region] [ARRAY [OBJECT gRNA]]]]
```

where `query-region` has schema,

```
{
"name": [STRING Region Name],
"organism": [STRING Organism Name],
"coords": [[STRING Chromosome], [INT Start], [INT End]]
}
```

and `gRNA` has schema,

```
{
    "sequence": [STRING Nucleotide Sequence],
    "start": [INT Start coordinate],
    "end": [INT End coordinate],
    "direction": [STRING ["positive" | "negative"]],
    "annotations": [ARRAY [STRING annotation]],
    "off-targets": [ARRAY [OBJECT off-target]], // OPTIONAL
    "specificity": [FLOAT Specificity score], // OPTIONAL
    "cutting-efficiency": [FLOAT Cutting efficiency score], // OPTIONAL
}
```

## Error Response

**Condition**: Job was not successfully completed.
**Code**: `404 Not Found`
