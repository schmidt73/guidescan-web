# Gene Database
The gene database is a SQL database that stores all sorts of
information about genes across various organisms. Currently, it stores
their location and associated gene symbols for use in name resolution
when a user submits a query as a gene symbol or ID. 

Each gene is uniquely identified by an integer using the Entrez Gene
nomenclature from NCBI. The relevant article can be found here:

https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3013746/

To initialize the database run the following sequence of commands:

```shell
$ lein with-profile create-gene-db uberjar
$ java -jar target/create-gene-db.jar [jdbc-db-url] 
```

Note that this will DROP all tables in the database named
 - `genes`
 - `chromosomes`

As an example `jdbc-db-url` you can use the following for postgresql:

```shell
jdbc:postgresql://localhost/guidescan
```

Note that the appropriate database driver will have to be added to the
classpath (that is the `project.clj` file) for everything to work.

## Updating Gene Database

To add the set of gene symbols for a given organism to the database,
find the organism on the NCBI FTP server: https://ftp.ncbi.nih.gov/genomes.

Then, download the two genomes assembly a `.gtf.gz` file and the
genomes chromosome mapping file named `chr2acc`.

As an example, the two files here for C. Elegans are located here:
  - [chr2acc](https://ftp.ncbi.nih.gov/genomes/refseq/invertebrate/Caenorhabditis_elegans/latest_assembly_versions/GCF_000002985.6_WBcel235/GCF_000002985.6_WBcel235_assembly_structure/Primary_Assembly/assembled_chromosomes/chr2acc)
  - [organism.gtf.gz](https://ftp.ncbi.nih.gov/genomes/refseq/invertebrate/Caenorhabditis_elegans/latest_assembly_versions/GCF_000002985.6_WBcel235/GCF_000002985.6_WBcel235_genomic.gtf.gz)

Once the files are downloaded, run the following script to add the
organism to the database:

```shell
$ lein with-profile add-organism uberjar
$ java -jar target/add-organism.jar [jdbc-db-url] [organism.gtf.gz] [chr2acc] [organism-name]
```

Ensure that the organism name matches what is used in your
configuration file.
