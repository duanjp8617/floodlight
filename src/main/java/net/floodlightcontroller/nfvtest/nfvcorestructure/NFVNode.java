package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.util.ArrayList;

import net.floodlightcontroller.nfvtest.nfvutils.HostServer.VmInstance;



public class NFVNode {
	
	public class SimpleSM{
		private int state;
		private final ArrayList<Integer> list;
		private int index;
		
		public SimpleSM(int listSize){
			list = new ArrayList<Integer>(listSize);
			this.state = NFVNode.IDLE;
			this.index = 0;
		}
		
		public void updateTransientState(int transientState){
			this.list.add(this.index, new Integer(transientState));
			this.index+=1;
			if(this.index == this.list.size()){
				
			}
		}
		
		public void updateFinalState(){
			int nOverload = 0;
			int nNormal = 0;
			int nIdle = 0;
			
			Integer overload = new Integer(NFVNode.OVERLOAD);
			Integer normal = new Integer(NFVNode.NORMAL);
			Integer idle = new Integer(NFVNode.IDLE);
			
			for(Integer elem : this.list){
				if(elem.equals(overload)){
					nOverload += 1;
				}
				if(elem.equals(normal)){
					nNormal += 1;
				}
				if(elem.equals(idle)){
					nIdle += 1;
				}
			}
			
			if((nOverload==nNormal)&&(nOverload==nIdle)){
				this.state = NFVNode.IDLE;
			}
			
		}
	}
	
	
	public class CircularList<E extends Comparable<E>>{
		private final ArrayList<E> list;
		private final int size;
		private int index;
		private boolean filledUp;
		
		public CircularList(int size){
			this.list = new ArrayList<E>(size);
			this.size = size;
			this.index = 0;
			this.filledUp = false;
		}
		
		public void add(E element){
			this.list.add(this.index, element);
			this.index = (this.index+1)%this.size;
			
			if(this.filledUp == false){
				if(this.index == 0){
					this.filledUp = true;
				}
			}
		}
		
		public boolean getFilledUp(){
			return this.filledUp;
		}
		
		public ArrayList<E> getCircularList(){
			return this.list;
		}
		
		public int getIndex(){
			return this.index;
		}
	}
	
	public class NFVNodeProperty{
		
		public final CircularList<Float> cpuUsage;
		public final CircularList<Float> memUsage; 
		
		public final CircularList<Integer> eth0RecvInt;
		public final CircularList<Long> eth0RecvPkt;
		public final CircularList<Integer> eth0SendInt;
	
		public final CircularList<Integer> eth1RecvInt;
		public final CircularList<Long> eth1RecvPkt;
		public final CircularList<Integer> eth1SendInt;
		
		public NFVNodeProperty(int listSize){
			cpuUsage = new CircularList<Float>(listSize);
			memUsage = new CircularList<Float>(listSize);
			
			eth0RecvInt = new CircularList<Integer>(listSize);
			eth0RecvPkt = new CircularList<Long>(listSize);
			eth0SendInt = new CircularList<Integer>(listSize);
			
			eth1RecvInt = new CircularList<Integer>(listSize);
			eth1RecvPkt = new CircularList<Long>(listSize);
			eth1SendInt = new CircularList<Integer>(listSize);
		}
	}
	//immutable field.
	public final VmInstance vmInstance;
	
	private NFVNodeProperty property;
	
	//node state: idel, normal, overload
	public static final int IDLE = 1;
	public static final int NORMAL = 2;
	public static final int OVERLOAD = 3;
	
	public NFVNode(VmInstance vmInstance){
		this.vmInstance = vmInstance;
		this.property = new NFVNodeProperty(15);
	}
	
	public String getChainName(){
		return this.vmInstance.serviceChainConfig.name;
	}
	
	public int getNumInterfaces(){
		return this.vmInstance.serviceChainConfig.nVmInterface;
	}
	
	public String getMacAddress(int whichMac){
		if(whichMac>=this.vmInstance.macList.size()){
			return "no-such-mac";
		}
		else{
			return this.vmInstance.macList.get(whichMac);
		}
	}
	
	public String getBridgeDpid(int whichMac){
		if(whichMac>=this.vmInstance.bridgeDpidList.size()){
			return "no-such-bridge";
		}
		else{
			return this.vmInstance.bridgeDpidList.get(whichMac);
		}
	}
	
	public int getPort(int whichMac){
		if(whichMac>=this.vmInstance.serviceChainConfig.nVmInterface){
			return -10;
		}
		else{
			return this.vmInstance.getPort(whichMac);
		}
	}
	
	public String getManagementMac(){
		return this.vmInstance.managementMac;
	}
	
	public void updateNodeProperty(Float cpuUsage, Float memUsage, 
								   Integer eth0RecvInt, Long eth0RecvPkt, Integer eth0SendInt,
								   Integer eth1RecvInt, Long eth1RecvPkt, Integer eth1SendInt){
		this.property.cpuUsage.add(cpuUsage);
		this.property.memUsage.add(memUsage);
		
		this.property.eth0RecvInt.add(eth0RecvInt);
		this.property.eth0RecvPkt.add(eth0RecvPkt);
		this.property.eth0SendInt.add(eth0SendInt);
		
		this.property.eth1RecvInt.add(eth1RecvInt);
		this.property.eth1RecvPkt.add(eth1RecvPkt);
		this.property.eth1SendInt.add(eth1SendInt);
	}
	
	private <E extends Comparable<E>> int checkTrend(int index, ArrayList<E> list){
		E lastElem = list.get(index);
		E firstElem = list.get((index-1)<0?(list.size()-1):(index-1));
		return firstElem.compareTo(lastElem);
	}
	
	private <E extends Comparable<E>> int checkStatus(ArrayList<E> list, E lowerT, E upperT){
		int largerThanUpperT = 0;
		int smallerThanLowerT = 0;
		int inBetween = 0;
		
		for(E elem : list){
			if(elem.compareTo(upperT)>0){
				largerThanUpperT +=1;
			}
			else if(elem.compareTo(lowerT)<0){
				smallerThanLowerT +=1;
			}
			else{
				inBetween += 1;
			}
		}
		
		int returnVal = 0;
		
		if((largerThanUpperT == smallerThanLowerT)&&(largerThanUpperT == inBetween)){
			returnVal = NFVNode.NORMAL;
		}
		
		if((largerThanUpperT >= smallerThanLowerT)&&(largerThanUpperT >= inBetween)){
			returnVal = NFVNode.OVERLOAD;
		}
		
		if((inBetween>=largerThanUpperT)&&(inBetween>=smallerThanLowerT)){
			returnVal = NFVNode.NORMAL;
		}
		
		if((smallerThanLowerT>=inBetween)&&(smallerThanLowerT>=largerThanUpperT)){
			returnVal = NFVNode.IDLE;
		}
		
		return returnVal;
	}
}
