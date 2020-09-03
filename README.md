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

Installation of the dependencies rather easy, thanks to the great
clojure package manager [Leiningen](https://leiningen.org/
"Leiningen") which is required for this project. Following the
instructions on the site should make it easy enough to install, but if
you are on macOS or Debian, running either,

``` shell
brew install leiningen
```

or 

``` shell
apt install leiningen
```

should do the trick. 

The only dependency that Leiningen relies on is a modern version of
Java. That is, Java >= 1.8.0, which should also be easy to install
with your systems package manager.

I list the dependencies here for convenience:

- JDK version 8 or higher
- [Leiningen](https://leiningen.org/ "Leiningen")

## Testing

To run the test suite and ensure everything is working correctly,
enter,

``` shell
lein test
```

from anywhere within the project.

## New Features

- BED and GTF/GFF file upload support
- Informative error messages
- Job queue
- SSL
- Annotations
- Simple deployment to cloud

## Currently Unsupported Features

- Flanking queries (easy)
- Fasta file upload (unknown difficulty)

## Comments on old code:

One of the main issues I have with the old codebase is that it does a
lot of input verification. It seems to me that everything would be
much simpler if instead of verifying input, they would simply attempt
to process it and let it fail.

One thing I need to do is document the 1 or 0 based invariants used
throughout the code and explicitly refer to conversions when they are
performed.

### Bug #1:

Number of off-targets reported is incorrect. For example,
the guideRNA:

chrV:911752-911774:-	TGAAAAATTTCGTAAAAAAT NGG	

reports 71 off-targets, when in reality there are only 66.
