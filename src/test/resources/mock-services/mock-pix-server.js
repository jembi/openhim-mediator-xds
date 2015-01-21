#!/usr/bin/env node
var net = require('net');

var HOST = '0.0.0.0'
var PORT = '12301'

var header = String.fromCharCode(013);
var footer = String.fromCharCode(034) + '\r';

var cannedResponse = 'MSH|^~\\&|PIX_MGR_OpenHIE_CR_MCAAT|OPENHIE|openhim|openhim-mediator-ohie-xds|201411210846||RSP^K23^RSP_K23|655ba7d4-71c0-450d-a5e1-69e97d68f944|P|2.5\r\n'
  + 'MSA|AA|84efdb97-d0f4-42bf-bdb5-1752eba0592b\r\n'
  + 'QAK|be7578db-4619-4cad-a320-38bad0b7cccf|OK\r\n'
  + 'PID|||f10f8d972aba4fd^^^&1.3.6.1.4.1.21367.2005.13.20.3000&ISO^PI||~^^^^^^S'

net.createServer(function(c) {

  c.on('error', function(err) {
    console.log(err);
  });

  c.on('data', function(chunk) {
    console.log('Recieved message chunk:\n' + chunk + '\n\n');

    if (chunk.toString().indexOf(footer) != -1) {
      c.end(header + cannedResponse + footer);
    }
  })

}).listen(PORT, HOST, function() {
  console.log('PIX server listenting on ' + HOST + ', port ' + PORT);
});
