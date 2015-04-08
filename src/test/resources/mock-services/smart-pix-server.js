#!/usr/bin/env node

// A sort-of smart mock pix server that remembers patient identifiers

var net = require('net');

var HOST = '0.0.0.0'
var PORT = '12301'

var header = String.fromCharCode(013);
var footer = String.fromCharCode(034) + '\r';

var cannedResponseKnown = 'MSH|^~\\&|PIX_MGR_OpenHIE_CR_MCAAT|OPENHIE|openhim|openhim-mediator-ohie-xds|201411210846||RSP^K23^RSP_K23|655ba7d4-71c0-450d-a5e1-69e97d68f944|P|2.5\r\n'
  + 'MSA|AA|84efdb97-d0f4-42bf-bdb5-1752eba0592b\r\n'
  + 'QAK|be7578db-4619-4cad-a320-38bad0b7cccf|OK\r\n'
  + 'PID|||fc133984036647e^^^&1.3.6.1.4.1.21367.2005.13.20.3000&ISO^PI||~^^^^^^S'

var cannedResponseUnknown = 'MSH|^~\\&|MESA_XREF|XYZ_HOSPITAL|VEMR|Connectathon|20141110155514+0000||RSP^K23^RSP_K23|7f0001011499a6a8b7f1|P|2.5\r\n'
  + 'MSA|AE|45dd0434-b8eb-4387-912e-6c16ba4e8bfe\r\n'
  + 'ERR||QPD^1^3^1^1|204^Unknown Key Identifier|E\r\n'
  + 'QAK|ef83b115-9626-462a-9cc0-1f4a95292b7e|AE\r\n'
  + 'QPD|IHE PIX Query|ef83b115-9626-462a-9cc0-1f4a95292b7e|1017295\S\\S\\S\ZAF\S\NI^^^SANID&SANID&SANID|^^^ECID&ECID&ECID';

var cannedResponseAckNewRegistration = 'MSH|^~\\&|OPENEMPI_PIX|OPENHIE|TEST_HARNESS_A|TEST|20141222123736-0500||ACK^A01|ac1f100514a73135d483|P|2.3.1\r\n'
  + 'MSA|AA|TEST-CR-04-20';

var knownPatients = {};

net.createServer(function(c) {
  var data = '';

  c.on('error', function(err) {
    console.log(err);
  });

  c.on('data', function(chunk) {
    data += chunk;

    if (chunk.toString().indexOf(footer) != -1) {
      console.log('Recieved message:\n' + data.replace('\r', '\n') + '\n\n');

      var regex = /(PID|QPD)\|[\w\s]*\|[\w\s\-\.]*\|([\w\.\^\&\~]+)/g;

      var pids = regex.exec(data)[2].split('~');
      var _i, _len;
      for (_i = 0, _len = pids.length; _i < _len; _i++) {
        if (pids[_i].indexOf('ISO') > -1) {
          pids[_i] = pids[_i].substr(0, pids[_i].indexOf('ISO'));
        }
      }
      console.log("PID: " + pids);

      if (data.indexOf('ADT^A04^ADT_A01') > -1) {
        console.log('New patient registration');

        for (_i = 0, _len = pids.length; _i < _len; _i++) {
          knownPatients[pids[_i]] = true;
        }
        c.end(header + cannedResponseAckNewRegistration + footer);

      } else {
        var known = false;
        for (_i = 0, _len = pids.length; _i < _len; _i++) {
          if (knownPatients[pids[_i]]) {
            known = true;
          }
        }

        if (known) {
          console.log('Patient is known');
          c.end(header + cannedResponseKnown + footer);
        } else {
          console.log('Patient is unknown');
          c.end(header + cannedResponseUnknown + footer);
        }
      }
    }
  })

}).listen(PORT, HOST, function() {
  console.log('PIX server listenting on ' + HOST + ', port ' + PORT);
});
