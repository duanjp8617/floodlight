package net.floodlightcontroller.nfvtest.nfvutils;

public class RouteTuple {
	public final static int TCP = 1;
	public final static int UDP = 2;
	public final static int OTHER = 3;
	
	public final int srcIp;
	public final int dstIp;
	public final int type;
	public final int srcPort;
	public final int dstPort;
	public final long dpid;
	
	public RouteTuple(int srcIp, int dstIp, int type, int srcPort, int dstPort, long dpid){
		this.srcIp = srcIp;
		this.dstIp = dstIp;
		this.type = type;
		this.srcPort = srcPort;
		this.dstPort = dstPort;
		this.dpid = dpid;
	}
	
	@Override
	public int hashCode() {
		int dpid = (int)this.dpid;
		dpid = (dpid<0)?(-1*dpid):dpid;
		
		final int prime = 5807;
		int result = prime;
		result = result+this.srcIp;
		result = result+this.dstIp;
		result = result+this.type;
		result = result+this.srcPort;
		result = result+this.dstPort;
		result = result+dpid;
		return result;	
	}
	
	@Override
    public boolean equals(Object obj) {
		if (this == obj)
            return true;
        if (!(obj instanceof RouteTuple))
            return false;
        RouteTuple other = (RouteTuple)obj;
        
        if(other.srcIp != this.srcIp){
        	return false;
        }
        if(other.dstIp != this.dstIp){
        	return false;
        }
        if(other.type != this.type){
        	return false;
        }
        if(other.srcPort != this.srcPort){
        	return false;
        }
        if(other.dstPort != this.dstPort){
        	return false;
        }
        if(other.dpid != this.dpid){
        	return false;
        }
        return true;
        
	}
}
