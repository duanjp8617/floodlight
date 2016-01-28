package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.nfvtest.nfvslaveservice.ServiceChainHandler;
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
			for(int i=0; i<this.listSize; i++){
				this.list.add(new Integer(0));
			}
		}
		
		public void updateTransientState(int transientState){
			this.list.set(this.index, new Integer(transientState));
			this.index+=1;
			if(this.index == this.listSize){
				updateFinalState();
				this.index = 0;
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
		
		public CircularList(int size, E e){
			this.list = new ArrayList<E>(size);
			this.size = size;
			this.index = 0;
			this.filledUp = false;
			for(int i=0; i<this.size; i++){
				this.list.add(e);
			}
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
		
		public E peekLatestVal(){
			return this.list.get((this.index+this.size-1)%this.size);
		}
	}
	
	public class NFVNodeProperty{
		private final SimpleSM cpuState;
		private final SimpleSM recvBdwState;
		private final SimpleSM recvPktState;
		
		private final int cpuThresh;
		private final long recvBdwThresh;
		private final long recvPktThresh;
	
		public final CircularList<Float> cpuUsage;
		public final CircularList<Long> recvBdw;
		public final CircularList<Long> recvPkt;
		 
		public NFVNodeProperty(int listSize, int cpuThresh, long recvBdwThresh, long recvPktThresh){
			cpuUsage = new CircularList<Float>(listSize, new Float(0));
			recvBdw = new CircularList<Long>(listSize, new Long(0));
			recvPkt = new CircularList<Long>(listSize, new Long(0));
			
			cpuState = new SimpleSM(listSize);
			recvBdwState = new SimpleSM(listSize);
			recvPktState = new SimpleSM(listSize);
			
			this.cpuThresh = cpuThresh;
			this.recvBdwThresh = recvBdwThresh;
			this.recvPktThresh = recvPktThresh;
		}
		
		public boolean checkIdle(){
			int state = checkStatus(recvPkt.getCircularList(), new Long(10), new Long(20));
			if(state == NFVNode.IDLE){
				return true;
			}
			else{
				return false;
			}
		}
		
		public void updateNodeProperty(Float cpuUsage, Long recvBdw, Long recvPkt){
				this.cpuUsage.add(cpuUsage);
				this.recvBdw.add(recvBdw);
				this.recvPkt.add(recvPkt);		
		}
		
		public int getNodeState(){
			if(cpuUsage.getFilledUp()&&(cpuThresh!=-1)){
				cpuState.updateTransientState(checkStatus(cpuUsage.getCircularList(), 
														  new Float(cpuThresh/2),
														  new Float(cpuThresh)));
			}
			if(recvPkt.getFilledUp()&&(recvPktThresh!=-1)){
				recvPktState.updateTransientState(checkStatus(recvPkt.getCircularList(), 
														  new Long(recvPktThresh/2),
														  new Long(recvPktThresh)));
			}
			if(recvBdw.getFilledUp()&&(recvBdwThresh!=-1)){
				recvBdwState.updateTransientState(checkStatus(recvBdw.getCircularList(), 
														  new Long(recvBdwThresh/2),
														  new Long(recvBdwThresh)));
			}
			
			int[] stateList = new int[3];
			stateList[0] = cpuState.getState();
			stateList[1] = recvPktState.getState();
			stateList[2] = recvBdwState.getState();
			
			int nOverload = 0;
			
			for(int i=0; i<3; i++){
				if(stateList[i] == NFVNode.OVERLOAD){
					nOverload+=1;
				}
			}
			
			if(nOverload > 1){
				return NFVNode.OVERLOAD;
			}
			else{
				return NFVNode.NORMAL;
			}
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
	private int scalingInterval;
	private final Logger logger =  LoggerFactory.getLogger(NFVNode.class);
	private int index;
	
	//node state: idel, normal, overload
	public static final int IDLE = 1;
	public static final int NORMAL = 2;
	public static final int OVERLOAD = 3;
	
	public NFVNode(VmInstance vmInstance){
		this.vmInstance = vmInstance;
		this.property = new NFVNodeProperty(15, vmInstance.serviceChainConfig.getStageVmInfo(vmInstance.stageIndex).cpuThreshold,
				vmInstance.serviceChainConfig.getStageVmInfo(vmInstance.stageIndex).inputBandwidth, 
				vmInstance.serviceChainConfig.getStageVmInfo(vmInstance.stageIndex).inputPktNum);
		this.state = NFVNode.IDLE;
		this.scalingInterval = -1;
	}
	
	public void setIndex(int index){
		this.index = index;
	}
	
	public int getIndex(){
		return this.index;
	}
	
	//scalingInterval is used to record at which interval 
	//the NFVNode is put into the scaleDownList
	public void setScalingInterval(int newScalingInterval){
		this.scalingInterval = newScalingInterval;
	}
	
	public int getScalingInterval(){
		return this.scalingInterval;
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
	
	public String getManagementIp(){
		return this.vmInstance.managementIp;
	}
	
	public String getManagementMac(){
		return this.vmInstance.managementMac;
	}
	
	public void updateNodeProperty(Float cpuUsage, Long recvBdw, Long recvPkt, Long sendPkt){
		String stat = cpuUsage.toString()+" "+recvBdw.toString()+" "+recvPkt.toString()+" "+sendPkt.toString();
		
		this.property.updateNodeProperty(cpuUsage, recvBdw, recvPkt);
		this.state = this.property.getNodeState();
		
		//if(this.vmInstance.stageIndex == 0){
		if(this.state == NFVNode.IDLE){
			String output = "Node-"+this.getManagementIp()+" is IDLE : "+stat;
			logger.info("{}", output);
		}
		if(this.state == NFVNode.NORMAL){
			String output = "Node-"+this.getManagementIp()+" is NORMAL : "+stat;
			logger.info("{}", output);
		}
		if(this.state == NFVNode.OVERLOAD){
			String output = "Node-"+this.getManagementIp()+" is OVERLOAD : "+stat;
			logger.info("{}", output);
		}
	}
	
	public int getState(){
		return this.state;
	}
	
	public long getCurrentRecvPkt(){
		return this.property.recvPkt.peekLatestVal().longValue();
	}
	
	public boolean checkIdle(){
		return this.property.checkIdle();
	}
}