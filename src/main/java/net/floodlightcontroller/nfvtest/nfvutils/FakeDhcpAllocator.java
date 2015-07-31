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
	
	FakeDhcpAllocator(MacAddressAllocator macAllocator, int startIp, int size){
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
		this.bridgeIp = this.intToString((startIp&0xFFFFFF00)+1);
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
	
	private byte[] intToByteArray(int value) {
	    byte[] byteArray = new byte[4];
	    byteArray[0] = (byte) ((value & 0xFF000000) >> 24);
	    byteArray[1] = (byte) ((value & 0x00FF0000) >> 16);
	    byteArray[2] = (byte) ((value & 0x0000FF00) >> 8);
	    byteArray[3] = (byte) (value & 0x000000FF);
	    return byteArray;
	}
	
	private String intToString(int value){
		byte[] byteArray = this.intToByteArray(value);
		StringBuilder sb = new StringBuilder();
		for(int i=0; i<byteArray.length; i++){
			sb.append(String.format("%d%s", byteArray[i], (i < byteArray.length - 1) ? "." : ""));
		}
		return sb.toString();
	}
}
