package net.floodlightcontroller.nfvtest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.VlanVid;

public class LibvirtNFVNode extends NFVNode {

	@Override
	protected void constructLocation(Map<String,String> LocationMap) {
		// TODO Auto-generated method stub
		for(String key : LocationMap.keySet()){
			String value = LocationMap.get(key);
			if(!location.containsKey(key)){
				location.put(key, value);
			}
		}
	}
	
	public static final String LOCATION1 = "RACK";
	public static final String LOCATION2 = "SERVER";
	public static final String LOCATION3 = "IP";
	public static final String[] LOCATIONTABLE = new String[] {
		LOCATION1,
		LOCATION2,
		LOCATION3
	};
	
	LibvirtNFVNode(Map<String,String> locationMap,
				   Collection<IPv4Address> argumentIpv4s,
				   Collection<MacAddress> argumentMacs,
				   Collection<VlanVid> argumentVlans,
				   String argumentStage,
				   Integer argumentNodeIndex){
		location = new HashMap<String, String>();
		ipv4s = new ArrayList<IPv4Address>(argumentIpv4s);
		macs = new ArrayList<MacAddress>(argumentMacs);
		vlans = new ArrayList<VlanVid>(argumentVlans);
		
		constructLocation(locationMap);
		stage = argumentStage;
		nodeIndex = argumentNodeIndex;
		uuid = UUID.randomUUID();
	}

}
