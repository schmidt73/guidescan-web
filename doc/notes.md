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

