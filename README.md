# guidescan-web

![Guidescan 2.0 Webpage](https://i.imgur.com/KEps36y.png)

Guidescan-web is the REST back-end for the rewritten, modern Guidescan
website. We hope that this rewrite alleviates many of the problems in
the old site, while allowing Guidescan to continue to thrive as a
useful tool for biologists going forward.

# REST API

The website exposes a REST API that services gRNA queries. At the
present, all endpoints are open and no authentication is required for
access to any of them.

## Query Submission

The query endpoint allows users to submit queries to the service which
are submitted to a job queue for processing.

* [Query](doc/rest_api/query.md): `ANY /query`

## Job 

Job endpoints allow users to get the current job status as well as the
result of successful queries.

* [Job Status](/doc/rest_api/job_status.md): `GET /job/status/:id{[0-9]+}`
* [Job Result](/doc/rest_api/job_result.md): `GET /job/status/:format{csv|json|bed}/:id{[0-9]+}`

## Info

Info endpoints give information about the types of gRNA queries that
are serviced by this API. Currently, there is one endpoint that
returns the supported organisms and enzymes available.

* [Supported](/doc/rest_api/supported_info.md): `GET /info/supported`

# Installation and Deployment

## Dependencies

Installation of the dependencies is rather easy, thanks to the great
Clojure package manager [Leiningen](https://leiningen.org/
"Leiningen") which is required for this project. Following the
instructions on the site should make it easy enough to install, but if
you are on macOS or Debian, running either,

``` shell
$ brew install leiningen
```

or 

``` shell
$ apt install leiningen
```

should do the trick. 

The only dependency that Leiningen relies on is an up-to-date version
of Java. That is, Java >= 1.8.0, which can also be installed with your
systems package manager.

I list the dependencies here for convenience:

- JDK version 8 or higher
- [Leiningen](https://leiningen.org/ "Leiningen")

## Deployment

There are two ways to deploy the application: either by packaging the
code into a standalone JAR file that can be immediately ran on any
system with an up-to-date version of Java, or by using Leiningen to
run the project for you. Only the former approach will be described
here as it is the preferred method.

First, bundle the code up into an uberjar file by running (read 
[what is an uberjar](https://stackoverflow.com/questions/11947037/what-is-an-uber-jar 
"what is an uberjar")),

``` shell
$ lein with-profile prod uberjar
Retrieving org/clojure/tools.cli/1.0.194/tools.cli-1.0.194.pom from central
Retrieving org/clojure/pom.contrib/1.0.0/pom.contrib-1.0.0.pom from central
Retrieving org/clojure/tools.cli/1.0.194/tools.cli-1.0.194.jar from central
...
Compiling guidescan-web.bam.db
Compiling guidescan-web.config
Compiling guidescan-web.core
Compiling guidescan-web.genomics.annotations
Compiling guidescan-web.genomics.grna
Compiling guidescan-web.query.jobs
Compiling guidescan-web.query.parsing
Compiling guidescan-web.query.process
Compiling guidescan-web.query.render
Compiling guidescan-web.routes
Created /home/ec2-user/guidescan-web/target/guidescan-web-2.0.jar
Created /home/ec2-user/guidescan-web/target/guidescan-web-2.0-standalone.jar
```

This will install all the dependencies and compile the code, generating
two jar files:

``` shell
target/guidescan.jar-THIN
target/guidescan.jar
```

The first jar file does not include all the dependencies, so if you
attempt to run the file without linking in the dependencies, it will
fail. The second jar file, however, is completely self contained and
includes everything it needs. You can send it off to another server
for deployment if you desire. Nothing else is needed.

To run it, simply type,

``` shell
$ java -jar guidescan.jar
```

though I recommend increasing the available memory to at least 8GB with the following flag,

``` shell
$ java -Xmx8G -jar guidescan.jar
Guidescan 2.0 Webserver

Usage: java -jar guidescan-web.jar [options] -c CONFIG 

Options:
  -p, --port PORT        8000       Port number
  -H, --hostname HOST    localhost  Hostname
  -A, --job-age D:H:M:S  {:days 1}  The amount of time job results will be stored in queue prior to being deleted.
  -c, --config CONFIG               EDN file for program configuration.
  -v                                Verbosity level
      --example-config              Prints out an example configuration.
  -h, --help                        Displays this help message.

Please refer to https://github.com/schmidt73/guidescan-web page for more information.
```

## Configuration

**IMPORTANT MUST READ**

Configuration is an essential part of the deployment process as the
program will need access to databases and information specific to each
organism:cas-enzyme combination. Configuration is done at *runtime* to
allow for maximum portability. Configuration is not tucked away during
compilation.

To configure the web server simply supply a config.edn file to the
program.

An example config can be found in the codebase, or by running,

``` shell
$ java -jar guidescan-web-2.0-standalone.jar --example-config 
```

or

```shell
$ lein run --example-config
```

The config file should be straightforward to parse even without
an in-depth understanding of the EDN format.

The config first defines the available organisms and cas-enzymes that
the webserver has access to. For example,

``` clojure
{:available-organism ["ce11" "hg38" "mm10"]
 :available-cas-enzymes ["cas9" "ascpf1"]}
```

defines 6 = 3*2 total organism:enzyme pairs. That is, 6 databases that
the web server must have access to.

To link the guideRNA databases in correctly, there is an entry that
maps these organism:enzyme pairs to the database location. It looks
like this:

``` clojure
{:grna-database-path-prefix "/home/schmidt73/Desktop/guidescan/guidescan-website/database"
 :grna-database-path-map {{:organism "ce11" :enzyme "cas9"} "cas9_ce11_all_guides.bam"
                          ...
                          {:organism "hg38" :enzyme "ascpf1"} "ascpf1_hg38_all_guides.bam"}}
```

where the `:grna-database-path-prefix` is added for convenience and
can be removed if not necessary.

To ensure that gene symbol names are correctly resolved, one must link
in the `gene-symbol` database. All information about correctly
generating this database can be found at
[gene-db](doc/databases/intro.md). Once the DB is generated, it is linked
in by adding the following entry to the configuration:

``` clojure
{:db-spec {:classname "org.postgresql.Driver"
           :jdbcUrl "jdbc:postgresql:guidescan?user=USER&pass=PASS"}}
```

Any SQL DB can be used, though the correct JDBC driver will needed to
be added to the classpath. This can be done simply by adding the
dependency to the `project.clj` file.

All that aside, the easiest way is to take a look at the example
config and tweak it to your needs.

# Testing

To run the test suite and ensure everything is working correctly,
enter,

``` shell
$ lein test
(trunacted ...)
Ran 45 tests containing 80 assertions.
0 failures, 0 errors.
```

from anywhere within the project.
