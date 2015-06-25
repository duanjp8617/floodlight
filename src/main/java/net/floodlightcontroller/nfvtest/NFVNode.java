package net.floodlightcontroller.nfvtest;

import java.util.Map;
import java.lang.String;
import java.util.Collection;
import java.util.UUID;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.VlanVid;


public abstract class NFVNode {
	Map<String, String> location;
	Collection<IPv4Address> ipv4s;
	Collection<MacAddress> macs;
	Collection<VlanVid> vlans;
	
	String stage;
	Integer nodeIndex;
	UUID uuid;
	
	protected abstract void constructLocation(Map<String,String> LocationMap);
	
	Map<String, String> getLocation(){
		return location;
	}
	
	Collection<IPv4Address> getIpv4(){
		return ipv4s;
	}
	
	Collection<MacAddress> getMac(){
		return macs;
	}
	
	Collection<VlanVid> getVlan(){
		return vlans;
	}
	
	String getStage(){
		return stage;
	}
	
	Integer getNodeIndex(){
		return nodeIndex;
	}
	
	UUID getUUID(){
		return uuid;
	}
}
