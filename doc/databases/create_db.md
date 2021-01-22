# Database Creation

This step is required prior to updating the database with any genetic
information. __As a warning this step will overwrite all the current tables
in the database with the same name.__

## Instructions

To initialize the database run the following sequence of commands:

```shell
$ java -cp target/db-gen.jar create_gene_db [jdbc-db-url]
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
