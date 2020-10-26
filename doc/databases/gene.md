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

As an example `jdbc-db-url` you can use the following for postgresql:

```shell
jdbc:postgresql://localhost/guidescan
```

Note that the appropriate database driver will have to be added to the
classpath (that is the `project.clj` file) for everything to work.

## Updating Gene Database

To add the set of gene symbols for a given organism to the database,
find the organisms GTF/GFF file on the NCBI FTP server
(https://ftp.ncbi.nih.gov/genomes).

Once the file is downloaded, you can run the following script to
add the organism to the database:

```shell
$ lein with-profile add-organism uberjar
$ java -jar target/add-organism.jar [jdbc-db-url] [organism.gtf.gz]
```
