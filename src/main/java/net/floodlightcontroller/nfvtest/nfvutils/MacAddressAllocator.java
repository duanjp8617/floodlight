package net.floodlightcontroller.nfvtest.nfvutils;
import java.util.Random;
import java.util.HashMap;

public class MacAddressAllocator {
	final byte[] prefix;
	private long uniqueValue;
	final HashMap<String, Long> macUniqueValueMap;
	
	public MacAddressAllocator(byte[] prefix){
		this.prefix = prefix;
		macUniqueValueMap = new HashMap<String, Long>();
	}
	
	public synchronized String getMac(){
		String macAddr = macToString(generateRandomMac());
		while(macUniqueValueMap.containsKey(macAddr)){
			macAddr = macToString(generateRandomMac());
		}
		macUniqueValueMap.put(macAddr, new Long(getUniqueValue()));
		return macAddr;
	}
	
	public synchronized void deleteMac(String macAddr){
		if(macUniqueValueMap.containsKey(macAddr)){
			macUniqueValueMap.remove(macAddr);
		}
	}
	
	private synchronized long getUniqueValue(){
		uniqueValue+=2;
		return uniqueValue;
	}
	
	private synchronized byte[] generateRandomMac(){
		int count = 6 - prefix.length;
		byte[] randomBytes = new byte[count];
		new Random().nextBytes(randomBytes);
		byte[] randomMac = new byte[6];
		
		for(int i=0; i<6; i++){
			if(i<prefix.length){
				randomMac[i]=prefix[i];
			}
			else{
				randomMac[i]=randomBytes[i-prefix.length];
			}
		}
		return randomMac;
	}
	
	private synchronized String macToString(byte[] mac){
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < mac.length; i++) {
			sb.append(String.format("%02x%s", mac[i], (i < mac.length - 1) ? ":" : ""));		
		}
		return sb.toString();
	}
}
