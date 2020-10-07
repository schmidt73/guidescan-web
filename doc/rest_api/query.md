# GuideRNA Query

Submits a query to specific region(s) of the genome and returns all
guideRNAs with few off-targets that are within (or flanking) the
region(s) of interests.

**URL**: `/query`
**METHOD**: `GET` or `POST`
**Parameters**

The query parameters can be represented by a JSON object as follows:

```
{
    "query-text": [STRING Query Text],
    "query-file-upload": [MULTIPART_FORM_DATA Query File],
    "enzyme": [STRING CAS enzyme to query against],
    "organism": [STRING Organism to query against],
    "topn-value": [INT Keep only this many of the best gRNAs], // OPTIONAL
    "flanking": [INT Search this distance around genomic region], // OPTIONAL
    "filter-annotated": [BOOL Remove non-annotated queries from result] // OPTIONAL
}
```

## Query Text/File

The query can be either submitted raw or by a file upload. 

### Query Text
The query text consists of a a newline separated list of chromosomal
coordinates in the form,

`chromosome:start-end`

which represent regions of interest.

As an example, the query text string could be:

```
chr3:1-1000000	
chr8:17888-19000	
```

### Query File

The query file can be uploaded as either a TXT file, FASTA file, BED
file, or GTF/GFF file. When doing file upload, the following header
must be attached.

**Headers**: `Content-Type: multipart/form-data`

## Success Response

Query will always succeed, returning the job ID of the submitted
query.

**Condition**: None.
**Code**: `200 OK`
**Content**

```
{
   "job-id": [INT Job ID number]
}
```
