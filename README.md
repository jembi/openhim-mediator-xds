[![Build Status](https://travis-ci.org/jembi/openhim-mediator-xds.svg)](https://travis-ci.org/jembi/openhim-mediator-xds)

openhim-mediator-xds
====================

An [OpenHIM](http://openhim.org) mediator for [OpenHIE](http://ohie.org) supporting XDS.b-based workflows. It validates and enriches client, healthcare worker and facility information.

The mediator contains two endpoints:
* **/xdsrepository** - Validates and routes requests to an XDS.b Repository and will enrich Provide and Register Document Set.b (ITI-42) requests with client, healthcare worker and facility enterprise identifiers. All other requests will pass-through unaltered to the Repository.
* **/xdsregistry** - Validates and routes requests to an XDS.b Registry and will enrich Adhoc Query (Registry Stored Query ITI-18) requests with client enterprise identifiers. All other requests will pass-through unaltered to the Registry.

Document enrichment is supported via PIX and CSD requests to any compliant Client Registry or CSD Infomanager.

ATNA auditing is supported for requests.

# Usage
* `git clone https://github.com/jembi/openhim-mediator-xds.git`
* `cd openhim-mediator-xds`
* `mvn install`
* `java -jar target/mediator-xds-1.0.0-jar-with-dependencies.jar`
