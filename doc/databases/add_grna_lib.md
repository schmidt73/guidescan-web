# Guidescan2 Library Construction

The gRNA library design endpoint requires that the database be filled
with a sgRNA database consisting of a set of validated sgRNAs,
targeting, and non-targeting controls. The libraries are released as
supplementary tables in the corresponding paper and consist of CSV
files containing all the necessary information to populate the database.

## Instructions

First obtain the sgRNA library for a particular organism from either
the supplement of our paper or as output of the script,
[design_library.py](https://github.com/schmidt73/guidescan-lib-design/blob/master/design_library.py).

Then, run the following command to populate the database,

```shell
$ java -cp target/db-gen.jar add_grna_lib [jdbc-db-url] [csv] [essential-gene-list] [organism]
```

this will create a table labeled `library`.

