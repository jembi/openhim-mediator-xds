#!/usr/bin/env node
var http = require('http');
var fs = require('fs');

var HOST = '0.0.0.0'
var PORT = '12303'

var cannedResponse = fs.readFileSync("adhocQueryResponse_wSOAP.xml");

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
  console.log('Registry server listenting on ' + HOST + ', port ' + PORT);
});
