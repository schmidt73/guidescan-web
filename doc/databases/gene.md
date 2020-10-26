# Gene Database

The gene database is a SQL database that stores all sorts of
information about genes across various organisms. Currently, it stores
their location and associated gene symbols for use in name resolution
when a user submits a query as a gene symbol or ID. 

Each gene is uniquely identified by an integer using the Entrez Gene
nomenclature from NCBI. The relevant article can be found here:

https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3013746/

The gene databases are generated from the following two files, found
on the NCBI FTP server (https://ftp.ncbi.nih.gov/gene/DATA/):
   - gene_info.gz
   - gene2accession.gz
   
To update the SQL database is to load the most recent version of the
files into the database. You can do this with the following sequence
of commands:

```shell
$ wget https://ftp.ncbi.nih.gov/gene/DATA/gene2accession.gz
$ wget https://ftp.ncbi.nih.gov/gene/DATA/gene_info.gz
$ lein with-profile generate-gene-db uberjar
$ java -jar target/generate-gene-db.jar [jdbc-db-url] gene_info.gz gene2accession.gz
```

Note that this will DROP all tables in the database named
 - `gene_ids`
 - `gene_symbols`
 - `taxonomies`

As an example `jdbc-db-url` you can use the following for postgresql:

```shell
jdbc:postgresql://localhost/guidescan?user=fred&password=secret
```

Note that the appropriate database driver will have to be added to the
classpath (that is the `project.clj` file) for everything to work.
