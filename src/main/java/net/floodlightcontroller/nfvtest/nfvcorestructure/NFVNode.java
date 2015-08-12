package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.util.ArrayList;

import net.floodlightcontroller.nfvtest.nfvutils.HostServer.VmInstance;



public class NFVNode {
	
	public class SimpleSM{
		private int state;
		private final ArrayList<Integer> list;
		private final int listSize;
		private int index;
		
		public int getState(){
			return this.state;
		}
		
		public SimpleSM(int listSize){
			this.listSize = listSize;
			list = new ArrayList<Integer>(listSize);
			this.state = NFVNode.IDLE;
			this.index = 0;
		}
		
		public void updateTransientState(int transientState){
			this.list.set(this.index, new Integer(transientState));
			this.index+=1;
			if(this.index == this.listSize){
				updateFinalState();
				this.index = 0;
				this.list.clear();
			}
		}
		
		private void updateFinalState(){
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
				this.state = NFVNode.NORMAL;
			}
			else if((nOverload>=nNormal)&&(nOverload>=nIdle)){
				this.state = NFVNode.OVERLOAD;
			}
			else if((nNormal>=nOverload)&&(nNormal>=nIdle)){
				this.state = NFVNode.NORMAL;
			}
			else if((nIdle>=nNormal)&&(nIdle>=nOverload)){
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
			this.list.set(this.index, element);
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
		private final SimpleSM cpuState;
		private final SimpleSM memState;
		private final SimpleSM eth0RecvState;
		private final SimpleSM eth0SendState;
		private final SimpleSM eth1RecvState;
		private final SimpleSM eth1SendState;
		
		
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
			
			cpuState = new SimpleSM(listSize);
			memState = new SimpleSM(listSize);
			eth0RecvState = new SimpleSM(listSize);
			eth0SendState = new SimpleSM(listSize);
			eth1RecvState = new SimpleSM(listSize);
			eth1SendState = new SimpleSM(listSize);
		}
		
		public void updateNodeProperty(Float cpuUsage, Float memUsage, 
				   Integer eth0RecvInt, Long eth0RecvPkt, Integer eth0SendInt,
				   Integer eth1RecvInt, Long eth1RecvPkt, Integer eth1SendInt){
				this.cpuUsage.add(cpuUsage);
				this.memUsage.add(memUsage);

				this.eth0RecvInt.add(eth0RecvInt);
				this.eth0RecvPkt.add(eth0RecvPkt);
				this.eth0SendInt.add(eth0SendInt);

				this.eth1RecvInt.add(eth1RecvInt);
				this.eth1RecvPkt.add(eth1RecvPkt);
				this.eth1SendInt.add(eth1SendInt);
		}
		
		public int getNodeState(){
			if(cpuUsage.getFilledUp()){
				cpuState.updateTransientState(checkStatus(cpuUsage.getCircularList(), 
														  new Float(20.0),
														  new Float(80.0)));
			}
			if(memUsage.getFilledUp()){
				memState.updateTransientState(checkStatus(memUsage.getCircularList(),
														  new Float(10.0),
														  new Float(50.0)));
			}
			if(eth0SendInt.getFilledUp()){
				eth0SendState.updateTransientState(checkStatus(eth0SendInt.getCircularList(),
															   new Integer(10),
															   new Integer(100)));
			}
			if(eth1SendInt.getFilledUp()){
				eth1SendState.updateTransientState(checkStatus(eth1SendInt.getCircularList(),
															   new Integer(10),
															   new Integer(100)));
			}
			if(eth0RecvInt.getFilledUp()&&eth0RecvPkt.getFilledUp()){
				int recvPktTrend = checkTrend(eth0RecvPkt.getIndex(), eth0RecvPkt.getCircularList());
				int recvIntTrend = checkTrend(eth0RecvInt.getIndex(), eth0RecvInt.getCircularList());
				
				int recvPktTState = checkStatus(eth0RecvPkt.getCircularList(),
												new Long(100000),
												new Long(550000));
				int tState = 0;
				if(recvPktTState != NFVNode.OVERLOAD){
					tState = recvPktTState;
				}
				else{
					if(recvPktTrend == recvIntTrend){
						tState = NFVNode.NORMAL;
					}
					else{
						tState = NFVNode.OVERLOAD;
					}
				}
				
				eth0RecvState.updateTransientState(tState);
			}
			if(eth1RecvInt.getFilledUp()&&eth1RecvPkt.getFilledUp()){
				int recvPktTrend = checkTrend(eth1RecvPkt.getIndex(), eth1RecvPkt.getCircularList());
				int recvIntTrend = checkTrend(eth1RecvInt.getIndex(), eth1RecvInt.getCircularList());
				
				int recvPktTState = checkStatus(eth1RecvPkt.getCircularList(),
												new Long(100000),
												new Long(550000));
				int tState = 0;
				if(recvPktTState != NFVNode.OVERLOAD){
					tState = recvPktTState;
				}
				else{
					if(recvPktTrend == recvIntTrend){
						tState = NFVNode.NORMAL;
					}
					else{
						tState = NFVNode.OVERLOAD;
					}
				}
				
				eth1RecvState.updateTransientState(tState);
			}
			
			int[] stateList = new int[6];
			stateList[0] = cpuState.getState();
			stateList[1] = memState.getState();
			stateList[2] = eth0RecvState.getState();
			stateList[3] = eth0SendState.getState();
			stateList[4] = eth1RecvState.getState();
			stateList[5] = eth1SendState.getState();
			
			int nNormal = 0;
			int nIdle = 0;
			
			for(int i=0; i<6; i++){
				if(stateList[i] == NFVNode.OVERLOAD){
					return NFVNode.OVERLOAD;
				}
				else if(stateList[i] == NFVNode.NORMAL){
					nNormal += 1;
				}
				else if(stateList[i] == NFVNode.IDLE){
					nIdle += 1;
				}
			}
			
			if(nIdle == 6){
				return NFVNode.IDLE;
			}
			else{
				return NFVNode.NORMAL;
			}
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
			else if((largerThanUpperT >= smallerThanLowerT)&&(largerThanUpperT >= inBetween)){
				returnVal = NFVNode.OVERLOAD;
			}
			else if((inBetween>=largerThanUpperT)&&(inBetween>=smallerThanLowerT)){
				returnVal = NFVNode.NORMAL;
			}
			else if((smallerThanLowerT>=inBetween)&&(smallerThanLowerT>=largerThanUpperT)){
				returnVal = NFVNode.IDLE;
			}
			
			return returnVal;
		}
	}
	//immutable field.
	public final VmInstance vmInstance;
	
	private NFVNodeProperty property;
	private int state;
	
	//node state: idel, normal, overload
	public static final int IDLE = 1;
	public static final int NORMAL = 2;
	public static final int OVERLOAD = 3;
	
	public NFVNode(VmInstance vmInstance){
		this.vmInstance = vmInstance;
		this.property = new NFVNodeProperty(15);
		this.state = NFVNode.IDLE;
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
		this.property.updateNodeProperty(cpuUsage, memUsage, eth0RecvInt, eth0RecvPkt, 
										 eth0SendInt, eth1RecvInt, eth1RecvPkt, eth1SendInt);
		this.state = this.property.getNodeState();
	}
	
	public int getState(){
		return this.state;
	}
}
