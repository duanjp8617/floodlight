package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	}
	
	public class NFVNodeProperty{
		private final SimpleSM cpuState;
		private final SimpleSM memState;
		
		private final SimpleSM eth0RecvState;
		private final SimpleSM eth0SendState;
		private final SimpleSM eth1RecvState;
		private final SimpleSM eth1SendState;
		
		private final SimpleSM tranState;
	
		public final CircularList<Float> cpuUsage;
		public final CircularList<Float> memUsage; 
		
		public final CircularList<Integer> eth0RecvInt;
		public final CircularList<Long>    eth0RecvPkt;
		public final CircularList<Integer> eth0SendInt;
	
		public final CircularList<Integer> eth1RecvInt;
		public final CircularList<Long>    eth1RecvPkt;
		public final CircularList<Integer> eth1SendInt;
		
		public final CircularList<Integer> goodTran;
		public final CircularList<Integer> badTran;
		public final CircularList<Integer> srdSt250ms;
		public final CircularList<Integer> srdLt250ms;
		 
		public NFVNodeProperty(int listSize){
			cpuUsage = new CircularList<Float>(listSize, new Float(0));
			memUsage = new CircularList<Float>(listSize, new Float(0));
			
			eth0RecvInt = new CircularList<Integer>(listSize, new Integer(0));
			eth0RecvPkt = new CircularList<Long>(listSize, new Long(0));
			eth0SendInt = new CircularList<Integer>(listSize, new Integer(0));
			
			eth1RecvInt = new CircularList<Integer>(listSize, new Integer(0));
			eth1RecvPkt = new CircularList<Long>(listSize, new Long(0));
			eth1SendInt = new CircularList<Integer>(listSize, new Integer(0));
			
			cpuState = new SimpleSM(listSize);
			memState = new SimpleSM(listSize);
			
			eth0RecvState = new SimpleSM(listSize);
			eth0SendState = new SimpleSM(listSize);
			eth1RecvState = new SimpleSM(listSize);
			eth1SendState = new SimpleSM(listSize);
			
			goodTran = new CircularList<Integer>(6, new Integer(0));
			badTran = new CircularList<Integer>(6, new Integer(0));
			srdSt250ms = new CircularList<Integer>(6, new Integer(0));
			srdLt250ms = new CircularList<Integer>(6, new Integer(0));
			
			tranState = new SimpleSM(6);
		}
		
		public void updateTranProperty(Integer goodTran, Integer badTran,
				                       Integer srdSt250ms, Integer srdLt250ms){
			this.goodTran.add(goodTran);
			this.badTran.add(badTran);
			this.srdSt250ms.add(srdSt250ms);
			this.srdLt250ms.add(srdLt250ms);
		}
		
		public int getTranState(){
			if(goodTran.getFilledUp()&&badTran.getFilledUp()&&srdSt250ms.getFilledUp()
			   &&srdLt250ms.getFilledUp()){
				tranState.updateTransientState(checkTranStatus(goodTran.getCircularList(),
															   badTran.getCircularList(),
															   srdSt250ms.getCircularList(),
															   srdLt250ms.getCircularList()));
				return tranState.getState();
			}
			else{
				return NFVNode.NORMAL;
			}
			
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
														  new Float(30.0),
														  new Float(85.0)));
			}
			if(memUsage.getFilledUp()){
				memState.updateTransientState(checkStatus(memUsage.getCircularList(),
														  new Float(30.0),
														  new Float(70.0)));
			}
			if(eth0SendInt.getFilledUp()){
				eth0SendState.updateTransientState(checkStatus(eth0SendInt.getCircularList(),
															   new Integer(10),
															   new Integer(20)));
			}
			if(eth1SendInt.getFilledUp()){
				eth1SendState.updateTransientState(checkStatus(eth1SendInt.getCircularList(),
															   new Integer(10),
															   new Integer(20)));
			}
			if(eth0RecvInt.getFilledUp()&&eth0RecvPkt.getFilledUp()){
				eth0RecvState.updateTransientState(checkRecvStatus(eth0RecvInt.getCircularList(),
																   eth0RecvPkt.getCircularList(),
																   (float)18));
			}
			if(eth1RecvInt.getFilledUp()&&eth1RecvPkt.getFilledUp()){
				eth1RecvState.updateTransientState(checkRecvStatus(eth1RecvInt.getCircularList(),
						   									       eth1RecvPkt.getCircularList(),
						   									       (float)18));
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
		
		private int checkTranStatus(ArrayList<Integer> goodTranList, ArrayList<Integer> badTranList,
									ArrayList<Integer> srdSt250List, ArrayList<Integer> srdLt250List){
			float totalGoodTran = 0;
			float totalBadTran = 0;
			float totalSrdSt250 = 0;
			float totalSrdLt250 = 0;
			
			for(int i=0; i<goodTranList.size(); i++){
				totalGoodTran += goodTranList.get(i).floatValue();
				totalBadTran += badTranList.get(i).floatValue();
				totalSrdSt250 += srdSt250List.get(i).floatValue();
				totalSrdLt250 += srdLt250List.get(i).floatValue();
			}
			
			float tranRatio = ((totalGoodTran+totalBadTran)==0)?1:
				                                  (totalGoodTran/(totalGoodTran+totalBadTran));
			float srdRatio = ((totalSrdSt250+totalSrdLt250)==0)?1:
                                                  (totalSrdSt250/(totalSrdSt250+totalSrdLt250));
				                                  
			
			if( (tranRatio>=0.99) && (srdRatio>=0.95) ){
				return NFVNode.NORMAL;
			}
			else{
				return NFVNode.OVERLOAD;
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
		
		private int checkRecvStatus(ArrayList<Integer> intList, ArrayList<Long> pktList, float t){
			int nOverload = 0;
			int nIdle = 0;
			
			for(int i=0; i<intList.size(); i++){
				float val = pktList.get(i).floatValue()/intList.get(i).floatValue();
				if(val>=t){
					nOverload+=1;
				}
				else{
					nIdle+=1;
				}
			}
		
			if(nOverload>=nIdle){
				return NFVNode.OVERLOAD;
			}
			else{
				return NFVNode.IDLE;
			}
		}
	}
	//immutable field.
	public final VmInstance vmInstance;
	
	private NFVNodeProperty property;
	private int state;
	private int tranState;
	private int activeFlows;
	private static Logger logger;
	private int scalingInterval;
	
	private int index;
	
	//node state: idel, normal, overload
	public static final int IDLE = 1;
	public static final int NORMAL = 2;
	public static final int OVERLOAD = 3;
	
	public NFVNode(VmInstance vmInstance){
		this.vmInstance = vmInstance;
		this.property = new NFVNodeProperty(4);
		this.state = NFVNode.IDLE;
		this.tranState = NFVNode.NORMAL;
		this.activeFlows = 0;
		logger = LoggerFactory.getLogger(NFVNode.class);
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
	
	public void updateNodeProperty(Float cpuUsage, Float memUsage, 
								   Integer eth0RecvInt, Long eth0RecvPkt, Integer eth0SendInt, Long eth0SendPkt,
								   Integer eth1RecvInt, Long eth1RecvPkt, Integer eth1SendInt, Long eth1SendPkt){
		String stat = cpuUsage.toString()+" "+memUsage.toString()+" "+eth0RecvInt.toString()+" "+
					  eth0RecvPkt.toString()+" "+eth0SendInt.toString()+" "+eth0SendPkt.toString()+" "+eth1RecvInt.toString()
					  +" "+eth1RecvPkt.toString()+" "+eth1SendInt.toString()+" "+eth1SendPkt.toString();
		float recvPkt = eth0RecvPkt.floatValue();
		float recvInt = eth0RecvInt.floatValue();
		stat = stat+" "+new Float(recvPkt/recvInt).toString();
		
		this.property.updateNodeProperty(cpuUsage, memUsage, eth0RecvInt, eth0RecvPkt, 
										 eth0SendInt, eth1RecvInt, eth1RecvPkt, eth1SendInt);
		this.state = this.property.getNodeState();
		
		//if(this.vmInstance.stageIndex == 0){
		if(this.state == NFVNode.IDLE){
			String output = "Node-"+this.getManagementIp()+" is IDLE : "+stat;
			//logger.info("{}", output);
		}
		if(this.state == NFVNode.NORMAL){
			String output = "Node-"+this.getManagementIp()+" is NORMAL : "+stat;
			//logger.info("{}", output);
		}
		if(this.state == NFVNode.OVERLOAD){
			String output = "Node-"+this.getManagementIp()+" is OVERLOAD : "+stat;
			//logger.info("{}", output);
		}
	}
	
	public void updateTranProperty(Integer goodTran, Integer badTran, Integer srdSt250ms, Integer srdLt250ms){
		String stat = goodTran.toString()+" "+badTran.toString()+" "+srdSt250ms.toString()+" "+srdLt250ms.toString()+" ";
		float p1 = goodTran.floatValue()/(goodTran.floatValue()+badTran.floatValue());
		float p2 = srdSt250ms.floatValue()/(srdSt250ms.floatValue()+srdLt250ms.floatValue());
		stat = stat + new Float(p1).toString() + " " + new Float(p2).toString();
		
		this.property.updateTranProperty(goodTran, badTran, srdSt250ms, srdLt250ms);
		this.tranState = this.property.getTranState();
		
		if(this.tranState == NFVNode.IDLE){
			String output = "Tran Node-"+this.getManagementIp()+" is IDLE : "+stat;
			//logger.info("{}", output);
		}
		if(this.tranState == NFVNode.NORMAL){
			String output = "Tran Node-"+this.getManagementIp()+" is NORMAL : "+stat;
			//logger.info("{}", output);
		}
		if(this.tranState == NFVNode.OVERLOAD){
			String output = "Tran Node-"+this.getManagementIp()+" is OVERLOAD : "+stat;
			//logger.info("{}", output);
		}
		
	}
	
	public int getState(){
		return this.state;
	}
	
	public int getTranState(){
		return this.tranState;
	}
	
	public void addActiveFlow(){
		this.activeFlows += 1;
	}
	
	public void deleteActiveFlow(){
		this.activeFlows -= 1;
	}
	
	public int getActiveFlows(){
		return this.activeFlows;
	}
}
