#!/usr/bin/env node
var http = require('http');

var HOST = '0.0.0.0'
var PORT = '12302'

var cannedResponse = "<CSD xmlns='urn:ihe:iti:csd:2013'>\n"
  + "  <serviceDirectory/>\n"
  + "  <organizationDirectory/>\n"
  + "  <facilityDirectory>\n"
  + "    <facility entityID='urn:oid:2.25.309768652999692686176651983274504471835.646.1.615351552068889518564164611046405512878087'>\n"
  + "      <!-- POTENTIALLY LARGE AMOUNT OF CONTENT ON THE FACILITY -->\n"
  + "    </facility>\n"
  + "  </facilityDirectory>\n"
  + "  <providerDirectory>\n"
  + "    <provider entityID='urn:oid:2.25.309768652999692686176651983274504471835.646.1.615351552068889518564164611046405512878087'>\n"
  + "      <!-- POTENTIALLY LARGE AMOUNT OF CONTENT ON THE PROVIDER -->\n"
  + "    </provider>\n"
  + "  </providerDirectory>\n"
  + "</CSD>\n"

http.createServer(function(req, res) {
  console.log('Recieved HTTP ' + req.method + ' request to ' + req.url);
  req.on('data', function(chunk){
    console.log('Recieved body chunk: ' + chunk);
  });

  res.writeHead(200, {
    'Content-Length': cannedResponse.length,
    'Content-Type': 'text/plain' });
  res.end(cannedResponse);
}).listen(PORT, HOST, function() {
  console.log('CSD server listenting on ' + HOST + ', port ' + PORT);
});
