#!/bin/bash
while :
do
    echo '{"zone": "A"}' | nc -c localhost 12345
    echo '{"zone": "B"}' | nc -c localhost 12345
done
