#!/usr/bin/perl
#
# Mock ATNA server: Just a udp listener!
#
# Credits: http://blog.jgc.org/2012/12/listen-on-udp-port-and-dump-received.html

use strict;
use warnings;
use Socket;

socket(UDP, PF_INET, SOCK_DGRAM, getprotobyname("udp"));
bind(UDP, sockaddr_in(2861, INADDR_ANY));
print $_ while (<UDP>);
