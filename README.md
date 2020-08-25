# guidescan-web

This project is a complete rewrite of the Guidescan website source
code in Clojure. The goal is to be able to quickly implement new
features. I will update my progress here as I go.

## New Features

- BED and GTF/GFF file upload support
- Informative error messages
- Job queue
- SSL
- Simple deployment to cloud

## Unsupported Features

- Flanking queries
- Annotations
- Sorting
- Fasta file upload

## Comments on code

One of the main issues I have with the old codebase is that it does a
lot of input verification. It seems to me that everything would be
much simpler if instead of verifying input, they would simply attempt
to process it and let it fail.
