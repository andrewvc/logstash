#!/bin/bash
echo '{"zone": "A", "message": "Message for A", "seq": 1}' | nc -c -u localhost 54321
echo '{"zone": "B", "message": "Message for B", "seq": 1}' | nc -c -u localhost 54321
