#!/bin/sh

INPUT="screenlog.0"
NODE1="Node-192.168.64.33"
NODE2="Node-192.168.64.32"
NODE3="Node-192.168.64.31"
NODE4="Node-192.168.64.30"
NODE5="Node-192.168.64.29"
NODE6="Node-192.168.64.28"
NODE7="Node-192.168.64.27"

grep -n $NODE1 $INPUT > $NODE1
grep -n $NODE2 $INPUT > $NODE2
grep -n $NODE3 $INPUT > $NODE3
grep -n $NODE4 $INPUT > $NODE4
grep -n $NODE5 $INPUT > $NODE5
grep -n $NODE6 $INPUT > $NODE6
grep -n $NODE7 $INPUT > $NODE7

sed  -e "s/:/ /g" -e "s/INFO/ /" -e "s/$NODE1/ /" -e "s/is/ /" -e "s/\[n.f.n.n.NFVNode Thread-4\]/ /" -e "s/IDLE/0/" -e "s/OVERLOAD/2/" -e  "s/NORMAL/1/" $NODE1 > "$NODE1-final" 

sed  -e "s/:/ /g" -e "s/INFO/ /" -e "s/$NODE2/ /" -e "s/is/ /" -e "s/\[n.f.n.n.NFVNode Thread-4\]/ /" -e "s/IDLE/0/" -e "s/OVERLOAD/2/" -e  "s/NORMAL/1/" $NODE2 > "$NODE2-final" 

sed  -e "s/:/ /g" -e "s/INFO/ /" -e "s/$NODE3/ /" -e "s/is/ /" -e "s/\[n.f.n.n.NFVNode Thread-4\]/ /" -e "s/IDLE/0/" -e "s/OVERLOAD/2/" -e  "s/NORMAL/1/" $NODE3 > "$NODE3-final" 

sed  -e "s/:/ /g" -e "s/INFO/ /" -e "s/$NODE4/ /" -e "s/is/ /" -e "s/\[n.f.n.n.NFVNode Thread-4\]/ /" -e "s/IDLE/0/" -e "s/OVERLOAD/2/" -e  "s/NORMAL/1/" $NODE4 > "$NODE4-final" 

sed  -e "s/:/ /g" -e "s/INFO/ /" -e "s/$NODE5/ /" -e "s/is/ /" -e "s/\[n.f.n.n.NFVNode Thread-4\]/ /" -e "s/IDLE/0/" -e "s/OVERLOAD/2/" -e  "s/NORMAL/1/" $NODE5 > "$NODE5-final" 

sed  -e "s/:/ /g" -e "s/INFO/ /" -e "s/$NODE6/ /" -e "s/is/ /" -e "s/\[n.f.n.n.NFVNode Thread-4\]/ /" -e "s/IDLE/0/" -e "s/OVERLOAD/2/" -e  "s/NORMAL/1/" $NODE6 > "$NODE6-final" 

sed  -e "s/:/ /g" -e "s/INFO/ /" -e "s/$NODE7/ /" -e "s/is/ /" -e "s/\[n.f.n.n.NFVNode Thread-4\]/ /" -e "s/IDLE/0/" -e "s/OVERLOAD/2/" -e  "s/NORMAL/1/" $NODE7 > "$NODE7-final" 






