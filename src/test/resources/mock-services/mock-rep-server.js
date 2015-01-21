#!/usr/bin/env node
var http = require('http');
var fs = require('fs');

var HOST = '0.0.0.0'
var PORT = '12304'

var cannedPnrResponse = fs.readFileSync("pnrResponse_wSOAP.xml");
var cannedRetrieveResponse = fs.readFileSync("retrieveResponse_wSOAP.xml");

http.createServer(function(req, res) {
  console.log('Recieved HTTP ' + req.method + ' request to ' + req.url);
  body = ""
  req.on('data', function(chunk){
    console.log('Recieved body chunk: ' + chunk);
    body += chunk.toString();
  });

  req.on('end', function() {
    if (body.indexOf('urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b') > -1) {
      res.writeHead(200, {
        'Content-Length': cannedPnrResponse.length,
        'Content-Type': 'Multipart/Related; start-info="application/soap+xml"; type="application/xop+xml"; boundary="uuid:9d70af7a-1e95-4187-8860-de7c7662ce3c"' });
      res.end(cannedPnrResponse);
    } else if (body.indexOf('urn:ihe:iti:2007:RetrieveDocumentSet') > -1) {
      res.writeHead(200, {
        'Content-Length': cannedRetrieveResponse.length,
        'Content-Type': 'Multipart/Related; start-info="application/soap+xml"; type="application/xop+xml"; boundary="uuid:e59abb7b-d9c5-4f29-9854-e89b12b41e12"' });
      res.end(cannedRetrieveResponse);
    } else {
      res.end("What did you send me? I don't understand.");
    }
    console.log('Sent a response');
  });
    
}).listen(PORT, HOST, function() {
  console.log('Repository server listenting on ' + HOST + ', port ' + PORT);
});