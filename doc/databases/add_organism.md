# Updating Gene Database

The gene database can be used to resolve gene symbols or gene IDs to
their coordinates. We use the [NCBI RefSeq](https://www.ncbi.nlm.nih.gov/refseq/) 
annotation sets. Please
ensure that the annotation files used to update the database match the 
corresponding reference sequences used for Guidescan DB generation.

## Instructions

To update the databse with this information, follow the instructions
below.

Each gene is uniquely identified by an integer using the Entrez Gene
nomenclature from NCBI. The relevant article can be found here:

https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3013746/

To add the set of gene symbols for a given organism to the database,
find the organism on the NCBI FTP server: https://ftp.ncbi.nih.gov/genomes/refseq/.

Then, download the two genomes assembly a `.gtf.gz` file and the
genomes chromosome mapping file named `chr2acc`.

As an example, the two files here for C. Elegans are located here:
  - [chr2acc](https://ftp.ncbi.nih.gov/genomes/refseq/invertebrate/Caenorhabditis_elegans/latest_assembly_versions/GCF_000002985.6_WBcel235/GCF_000002985.6_WBcel235_assembly_structure/Primary_Assembly/assembled_chromosomes/chr2acc)
  - [organism.gtf.gz](https://ftp.ncbi.nih.gov/genomes/refseq/invertebrate/Caenorhabditis_elegans/latest_assembly_versions/GCF_000002985.6_WBcel235/GCF_000002985.6_WBcel235_genomic.gtf.gz)

Once the files are downloaded, run the following script to add the
organism to the database:

```shell
$ java -cp target/db-gen.jar add_organism [jdbc-db-url] [organism.gtf.gz] [chr2acc] [organism-name]
```

Ensure that the organism name matches what is used in your
configuration file.
