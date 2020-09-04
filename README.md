# guidescan-web

![Guidescan 2.0 Webpage](https://i.imgur.com/KEps36y.png)

Guidescan-web is a complete rewrite of the previous Guidescan website
for the modern era. With an emphasis on code quality and rigorous
testing, there should be little getting in your way when making
changes to the codebase. There should also be little friction when
deploying the app! Long gone are the days when reproducing the exact
environment necessary to host the site is a momentous task in of
itself. We hope that these changes allow Guidescan to continue to
thrive as a useful tool for biologists going forward, while
maintaining it's ability to adapt to new requirements.

# Installation and Deployment

## Dependencies

Installation of the dependencies is rather easy, thanks to the great
clojure package manager [Leiningen](https://leiningen.org/
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
system with an up-to-date version of Java, or by using leiningen to
run the project for you. Only the former approach will be described
here as it is the preferred method.

First, bundle the code up into an uberjar file by running (read 
[what is an uberjar](https://stackoverflow.com/questions/11947037/what-is-an-uber-jar 
"what is an uberjar")),

``` shell
$ lein uberjar
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
Created /home/schmidt73/Desktop/guidescan-web/target/guidescan-web-2.0.jar
Created /home/schmidt73/Desktop/guidescan-web/target/guidescan-web-2.0-standalone.jar
```

This will install all the dependencies and compile the code, generating
two jar files:

``` shell
target/guidescan-web-2.0.jar
target/guidescan-web-2.0-standalone.jar
```

The first jar file does not include all the dependencies, so if you
attempt to run the file without linking in the dependencies, it will
fail. The second jar file, however, is completely self contained and
includes everything it needs. You can send it off to another server
for deployment if you desire. Nothing else is needed.

To run it, simply type,

``` shell
$ java -jar guidescan-web-2.0-standalone.jar
```

though I recommend increasing the available memory to at least 8GB with the following flag,

``` shell
$ java -Xmx8G -jar guidescan-web-2.0-standalone.jar
Guidescan 2.0 Webserver

Usage: java -jar guidescan-web.jar [options] -c CONFIG 

Options:
  -p, --port PORT       8000       Port number
  -H, --hostname HOST   localhost  Hostname
  -c, --config CONFIG              EDN file for program configuration.
  -v                               Verbosity level
      --example-config             Prints out an example configuration.
  -h, --help                       Displays this help message.

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

Finally, there is an `:annotations-map` that takes organisms to their
gene body annotations. This is configured on a per organism basis.

All that aside, the easiest way is to take a look at the example
config and tweak it to your needs.

## Testing

To run the test suite and ensure everything is working correctly,
enter,

``` shell
$ lein test
(trunacted ...)
Ran 45 tests containing 80 assertions.
0 failures, 0 errors.
```

from anywhere within the project.
