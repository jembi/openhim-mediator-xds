Mock Services
=============

This directory contains a set of mock services to test the xds-mediator without
having to have other components setup. The default config in mediator.properties
is setup to use these mock servers.

To run one of the mock servers, just execute it in a terminal. Eg.
`./mock-pix-server.js`

You will need to have [nodejs](http://nodejs.org/) and [perl](https://www.perl.org/)
installed for these to work.

Also contained in this directory is a [postman](http://www.getpostman.com/)
collection that allows you to submit request to the OpenHIM and/or the xds-mediator
to see it work.
