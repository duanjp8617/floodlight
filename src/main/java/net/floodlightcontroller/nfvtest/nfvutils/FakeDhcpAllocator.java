package net.floodlightcontroller.nfvtest.nfvutils;

import net.floodlightcontroller.nfvtest.nfvutils.MacAddressAllocator;
import net.floodlightcontroller.nfvtest.nfvutils.Pair;

import java.util.HashMap;
import java.util.LinkedList;

public class FakeDhcpAllocator {
	public final String startIp;
	public final String endIp;

	public final String bridgeIp;
	public final String bridgeMac;
	
	private final HashMap<String, String> macIpMap;
	private final LinkedList<String> macQueue;
	private final HashMap<String, String> allocatedMacIpMap;
	
	FakeDhcpAllocator(MacAddressAllocator macAllocator, long startIp, int size){
		this.startIp = this.intToString(startIp);
		this.endIp = this.intToString(startIp+size-1);
		
		this.macIpMap = new HashMap<String, String>();
		this.macQueue = new LinkedList<String>();
		this.allocatedMacIpMap = new HashMap<String, String>();
		
		for(int i=0; i<size; i++){
			String macAddr = macAllocator.getMac();
			this.macQueue.push(macAddr);
			this.macIpMap.put(macAddr, this.intToString(startIp+i));
		}
		
		this.bridgeMac = macAllocator.getMac();
		this.bridgeIp = this.intToString((startIp&0xFFFFFFFFFFFFFF00l)+1);
	}
	
	public synchronized Pair<String, String> allocateMacIp(){
		if(this.macQueue.size()==0){
			return new Pair<String, String>("nil", "nil");
		}
		else{
			String macAddr = this.macQueue.pop();
			String ipAddr = this.macIpMap.get(macAddr);
			this.allocatedMacIpMap.put(macAddr, ipAddr);
			return new Pair<String, String>(macAddr, ipAddr);
		}
	}
	
	public synchronized void deallocateMacIp(Pair<String, String> macIpPair){
		String macAddr = macIpPair.first;
		
		if(this.allocatedMacIpMap.containsKey(macAddr)){
			this.allocatedMacIpMap.remove(macAddr);
			this.macQueue.push(macAddr);
		}
	}
	
	public HashMap<String, String> getMacIpMap(){
		return this.macIpMap;
	}
	
	private long[] intToByteArray(long value) {
	    long[] byteArray = new long[4];
	    byteArray[0] = ((value & 0xFFFFFFFFFF000000l) >> 24);
	    byteArray[1] = ((value & 0xFFFFFFFF00FF0000l) >> 16);
	    byteArray[2] = ((value & 0xFFFFFFFF0000FF00l) >> 8);
	    byteArray[3] = (value & 0xFFFFFFFF000000FFl);
	    return byteArray;
	}
	
	private String intToString(long value){
		long[] byteArray = this.intToByteArray(value);
		StringBuilder sb = new StringBuilder();
		for(int i=0; i<byteArray.length; i++){
			sb.append(String.format("%d%s", (int)byteArray[i], (i < byteArray.length - 1) ? "." : ""));
		}
		return sb.toString();
	}
}
