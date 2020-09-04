## New Features

- BED and GTF/GFF file upload support
- Informative error messages
- Job queue
- SSL
- Annotations
- Simple deployment to cloud

## Currently Unsupported Features

- Flanking queries (easy)
- Fasta file upload (unknown difficulty)

## Comments on old code:

One of the main issues I have with the old codebase is that it does a
lot of input verification. It seems to me that everything would be
much simpler if instead of verifying input, they would simply attempt
to process it and let it fail.

One thing I need to do is document the 1 or 0 based invariants used
throughout the code and explicitly refer to conversions when they are
performed.

### Bug #1:

Number of off-targets reported is incorrect. For example,
the guideRNA:

chrV:911752-911774:-	TGAAAAATTTCGTAAAAAAT NGG	

reports 71 off-targets, when in reality there are only 66.

### Bug #2:

The tag type specified in the gRNA database for hg38:cas9
is 'd' which is not a valid tag type.

```shell
$ samtools view /guidescan/databases/hg38/cas9/cas9_hg38_all_guides.bam | head -n 10 | tail -n 1
GGTGACGGGTGGCTCGGCTCNGG 16      chr1    17476   100     23M     *       0       0       CCNGAGCCGAGCCACCCGTCACC *       od:i:3  oc:i:1000       of:H:010000000000000058fa17a3ffffffff0300000000000000262eb640ffffffff0100000000000000855b0768000000000300000000000000262eb640ffffffff0000000000000000cd45516effffffff0300000000000000262eb640ffffffff0000000000000000d89c9979000000000300000000000000262eb640ffffffff00000000000000000d6a1513000000000300000000000000262eb640ffffffff0000000000000000385f7273000000000300000000000000262eb640ffffffff0000000000000000c038cb4f000000000300000000000000262eb640ffffffff0000000000000000db127b83ffffffff0300000000000000262eb640ffffffff0000000000000000a0d215adffffffff0300000000000000262eb640ffffffff   ds:d:0.425863   cs:Z:0.46931664
```

Whereas here in the remaining organisms it is correctly labeled as Z:

```shell
$ samtools view /guidescan/databases/mm10/cas9/cas9_mm10_all_guides.bam | head -n 10 | tail -n 1
ATCGATTGTGATTTGAGTCCNGG 0       chr10   3101527 100     23M     *       0       0       ATCGATTGTGATTTGAGTCCNGG *       od:i:3  oc:i:1000       of:H:00000000000000008a855070ffffffff030000000000000021343a5dffffffff       ds:Z:0.50239642496      cs:Z:0.9212121
```
