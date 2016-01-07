sudo ovs-vsctl del-br DATA-br0
sudo ovs-vsctl del-br DATA-br1
sudo ovs-vsctl del-br DATA-br2
sudo ovs-vsctl del-br DATA-br3
virsh net-destroy DATA-m
virsh net-destroy CONTROL-m
virsh net-destroy CONTROL-o
rm -f ~/nfvenv/xml/*
sudo rm -f ~/nfvenv/img/DATA*
sudo rm -f ~/nfvenv/img/CONTROL*
