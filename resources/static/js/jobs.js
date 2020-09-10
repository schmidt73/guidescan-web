genomeBrowser = null;

function createGenomeBrowser(igvElement, data, jobID, index) {
    genome = data[index][0]["organism"];
    startLocus = data[index][0]["coords"];
    startLocus = startLocus[0] + ":" + startLocus[1] + "-" + startLocus[2];

    options = {
        genome: genome,
        locus: startLocus,
        tracks: [
            {
                "name": "gRNAs",
                "url": "/job/get/bed/" + jobID,
                "format": "bed"
            }
        ]
    };

    igv.createBrowser(igvElement, options).then(
        function(browser) {
            genomeBrowser = browser;
        }
    );
}

function searchGenomeBrowser(locus) {
    if (genomeBrowser) {
        genomeBrowser.search(locus);
    }
}

/* Creates a header for a given genomic region that when clicked moves
   the genome browser to look at that region. */
function createSearchGenomeHeader(genomicRegion) {
    coords = genomicRegion["coords"];


    return header;
}

function buildCoordinateString(chr, grna) {
    return chr + ":" + grna["start"] + "-" + grna["end"];
}
