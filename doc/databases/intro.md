# Databases

The database used by the website for Guidescan is a PostgreSQL DB. The
database stores various bits of genomic information such as gene
symbol names, coordinates, and corresponding genomic elements. There
are various scripts for these different use cases. Each use case is
described below seperately.

The only mandatory component of the database is its schema. This can
be created from scratch with the `create_gene_db` target and is
required before running any of the other scripts.

## Scripts

To compile the scripts for database database generation, simply run:

```shell
$ lein with-profile database-generation uberjar
```

This will create a bundled jar containing all of the scripts required
for various database maintenance and generation tasks.

There are several different tasks supported:

* Database schema creation
    * `create_gene_db` target
    * instructions [here](./create_db.md)
* Updating the gene database with new organisms
    * `add_organism` target
    * instructions [here](./add_organism.md)
* Adding gRNA libraries
    * `add_grna_lib` target
    * instructions [here](./add_grna_lib.md)

   
