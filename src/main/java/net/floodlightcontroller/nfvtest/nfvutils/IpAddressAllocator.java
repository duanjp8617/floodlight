package net.floodlightcontroller.nfvtest.nfvutils;

public class IpAddressAllocator {
	private int uniqueValue;
	
	public IpAddressAllocator(byte first, byte second, byte third){
		byte[] prefix = new byte[4];
		prefix[0] = first;
		prefix[1] = second;
		prefix[2] = third;
		prefix[3] = 0;
		this.uniqueValue = this.byteArrayToInt(prefix);
	}
	
	public synchronized int allocateIp(){
		int returnVal = this.uniqueValue;
		this.uniqueValue += 0x00000100;
		return returnVal;
	}
	
	public int byteArrayToInt(byte[] byteArray) {
	    int value = (byteArray[3] << (24));
	    value |= (byteArray[2] & 0xFF) << (16);
	    value |= (byteArray[1] & 0xFF) << (8);
	    value |= (byteArray[0] & 0xFF);
	    return value;
	}
}
