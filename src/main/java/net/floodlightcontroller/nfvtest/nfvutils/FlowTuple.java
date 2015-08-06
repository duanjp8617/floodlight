package net.floodlightcontroller.nfvtest.nfvutils;

public class FlowTuple {
	public final static int TCP = 1;
	public final static int UDP = 2;
	public final static int OTHER = 3;
	
	private final int srcIp;
	private final int dstIp;
	private final int type;
	private final int srcPort;
	private final int dstPort;
	
	public FlowTuple(int srcIp, int dstIp, int type, int srcPort, int dstPort){
		this.srcIp = srcIp;
		this.dstIp = dstIp;
		this.type = type;
		this.srcPort = srcPort;
		this.dstPort = dstPort;
	}
	
	@Override
	public int hashCode() {
		final int prime = 5807;
		int result = prime;
		result = result+this.srcIp;
		result = result+this.dstIp;
		result = result+this.type;
		result = result+this.srcPort;
		result = result+this.dstPort;
		return result;	
	}
	
	@Override
    public boolean equals(Object obj) {
		if (this == obj)
            return true;
        if (!(obj instanceof FlowTuple))
            return false;
        FlowTuple other = (FlowTuple)obj;
        
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
        return true;
        
	}
}
