#!/bin/bash

echo $((echo -n -e '\x0B'$(cat "$1")'\x1C\x0D'; sleep 2;) | nc $2 $3 2>&1;)
