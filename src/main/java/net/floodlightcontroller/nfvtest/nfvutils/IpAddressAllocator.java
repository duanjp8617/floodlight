package net.floodlightcontroller.nfvtest.nfvutils;

public class IpAddressAllocator {
	private long uniqueValue;
	
	public IpAddressAllocator(long first, long second, long third){
		long[] prefix = new long[4];
		prefix[0] = first;
		prefix[1] = second;
		prefix[2] = third;
		prefix[3] = 0;
		this.uniqueValue = this.byteArrayToInt(prefix);
	}
	
	public synchronized long allocateIp(){
		long returnVal = this.uniqueValue;
		this.uniqueValue += 256;
		return returnVal;
	}
	
	public long byteArrayToInt(long[] byteArray) {
	    long value = ((byteArray[0] << 24)&0xFFFFFFFFFF000000l);
	    value |= ((byteArray[1] << 16)&0xFFFFFFFF00FF0000l);
	    value |= ((byteArray[2] << 8)&0xFFFFFFFF0000FF00l);
	    value |= (byteArray[3] & 0xFFFFFFFF000000FFl);
	    return value;
	}
}
