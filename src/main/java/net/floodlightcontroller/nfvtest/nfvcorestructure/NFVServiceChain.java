package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.*;

public class NFVServiceChain {
	
	public final ServiceChainConfig serviceChainConfig;
	
	private final Map<String, NFVNode> entryMacNodeMap;
	private final Map<String, NFVNode> exitMacNodeMap;
	private final Map<String, NFVNode> managementIpNodeMap;

	private final boolean[] scaleIndicators;
	
	private final List<Map<String, NFVNode>> workingNodeMaps;
	private final List<Deque<NFVNode>>       bufferNodeQueues;
	public final Map<String, NFVNode> destroyNodeMap;
	
	private int scalingInterval;
	private int maximumBufferingInterval = 5;
	
	private int dpPaths[][][];
	private int previousDpPaths[][][];
	private int nextDpPaths[][][];
	
	NFVServiceChain(ServiceChainConfig serviceChainConfig){
		this.serviceChainConfig = serviceChainConfig;
		this.managementIpNodeMap = new HashMap<String, NFVNode>();
		
		if(serviceChainConfig.nVmInterface == 3){
			this.entryMacNodeMap = new HashMap<String, NFVNode>();
			this.exitMacNodeMap = new HashMap<String, NFVNode>();
		}
		else{
			this.entryMacNodeMap = null;
			this.exitMacNodeMap = null;
		}
		
		this.workingNodeMaps = new ArrayList<Map<String, NFVNode>>();
		this.bufferNodeQueues = new ArrayList<Deque<NFVNode>>();
		for(int i=0; i<this.serviceChainConfig.stages.size(); i++){
			Map<String, NFVNode> nodeMap = new HashMap<String, NFVNode>();
			this.workingNodeMaps.add(nodeMap);
			
			Deque<NFVNode> bufferNodeQueue = new LinkedList<NFVNode>();
			this.bufferNodeQueues.add(bufferNodeQueue);
		}
		
		destroyNodeMap = new HashMap<String, NFVNode>();
		
		scaleIndicators = new boolean[this.serviceChainConfig.stages.size()];
		for(int i=0; i<scaleIndicators.length; i++){
			scaleIndicators[i] = false;
		}
	
		this.scalingInterval = 0;
		this.dpPaths = null;
		this.previousDpPaths = null;
		this.nextDpPaths = null;
	}
	
	public int getScalingInterval(){
		return (this.scalingInterval)%4;
	}
	
	public int[] getPreviousDpPaths(int srcDcIndex, int dstDcIndex){
		return this.previousDpPaths[srcDcIndex][dstDcIndex];
	}
	
	public int[] getCurrentDpPaths(int srcDcIndex, int dstDcIndex){
		return this.dpPaths[srcDcIndex][dstDcIndex];
	}
	
	public int[] getNextDpPaths(int srcDcIndex, int dstDcIndex){
		return this.nextDpPaths[srcDcIndex][dstDcIndex];
	}
	
	public synchronized  void addNextDpPaths(int[][][] nextDpPaths){
		if(this.serviceChainConfig.nVmInterface == 3){
			this.nextDpPaths = nextDpPaths;
		}
	}
	
	public synchronized  void addScalingInterval(){
		if(this.serviceChainConfig.nVmInterface == 3){
			this.scalingInterval += 1;
			previousDpPaths = dpPaths;
			dpPaths = nextDpPaths;
		}
	}
	
	public   int[] getProvision(){
		int provision[] = new int[this.serviceChainConfig.stages.size()];
		for(int i=0; i<provision.length; i++){
			provision[i] = this.workingNodeMaps.get(i).size();
		}
		return provision;
	}
	
	//The following functions are public interfaces for manipulating the buffer queue
	//the buffer queue is a double linked list. When a new node is added to the queue,
	//it will be tagged with the current scaling interval. When the node is removed from
	//the queue, the tag is removed.
	public   int bqSize(int stageIndex){
		return bufferNodeQueues.get(stageIndex).size();
	}
	
	public   void addToBqRear(NFVNode node){
		int stageIndex = node.vmInstance.stageIndex;
		node.setScalingInterval(this.scalingInterval);
		bufferNodeQueues.get(stageIndex).addLast(node);
	}
	
	public   NFVNode removeFromBqRear(int stageIndex){
		NFVNode node = bufferNodeQueues.get(stageIndex).pollLast();
		if(node!=null)
			node.setScalingInterval(-1);
		return node;
	}
	
	public   NFVNode removeFromBqHead(int stageIndex){
		NFVNode head = bufferNodeQueues.get(stageIndex).peek();
		if(head != null){
			if((scalingInterval-head.getScalingInterval())>maximumBufferingInterval){
				head = bufferNodeQueues.get(stageIndex).poll();
				head.setScalingInterval(-1);
				return head;
			}
			else{
				return null;
			}
		}
		else{
			return null;
		}
	}

	//The following 2 functions are public interfaces for manipulating the 
	//working node map
	public   void addWorkingNode(NFVNode node){
		if(node.vmInstance.serviceChainConfig.name.equals(serviceChainConfig.name)){
			Map<String, NFVNode> stageMap = this.workingNodeMaps.get(node.vmInstance.stageIndex);
			if(!stageMap.containsKey(node.getManagementIp())){
				stageMap.put(node.getManagementIp(), node);
			}
		}
	}
	
	public   void removeWorkingNode(NFVNode node){
		if(node.vmInstance.serviceChainConfig.name.equals(serviceChainConfig.name)){
			Map<String, NFVNode> stageMap = this.workingNodeMaps.get(node.vmInstance.stageIndex);
			if( (stageMap.containsKey(node.getManagementIp())) ){
				stageMap.remove(node.getManagementIp());
			}
		}
	}
	
	//The following 2 functions are public interfaces for manipulating the destroy
	//node map
	public   void addDestroyNode(NFVNode node){
		if(!destroyNodeMap.containsKey(node.getManagementIp())){
			destroyNodeMap.put(node.getManagementIp(), node);
		}
	}
	
	//The following 2 function are used to to add the node to the service chain.
	//once the nodes are added to the service chain, we will record the node in
	//possibly 3 maps.
	public   void addToServiceChain(NFVNode node){
		if(!this.managementIpNodeMap.containsKey(node.getManagementIp())){
			this.managementIpNodeMap.put(node.getManagementIp(), node);
			if(this.serviceChainConfig.nVmInterface == 3){
				this.entryMacNodeMap.put(node.vmInstance.macList.get(0), node);
				this.exitMacNodeMap.put(node.vmInstance.macList.get(1), node);
			}
		}
	}
	
	public   void removeFromServiceChain(NFVNode node){
		
		if(!this.managementIpNodeMap.containsKey(node.getManagementIp())){
			this.managementIpNodeMap.remove(node.getManagementIp());
			if(this.serviceChainConfig.nVmInterface == 3){
				this.entryMacNodeMap.remove(node.vmInstance.macList.get(0));
				this.exitMacNodeMap.remove(node.vmInstance.macList.get(1));
			}
		}
	}
	
	//this function returns an NFVNode whose state is not overload
	public   NFVNode getNormalWorkingNode(int stage){
		Map<String, NFVNode> stageMap = workingNodeMaps.get(stage);
		
		for(String key : stageMap.keySet()){
			NFVNode node = stageMap.get(key);
			if(node.getState() != NFVNode.OVERLOAD){
				return node;
			}
		}
		
		return null;
	}
	
	public   List<NFVNode> forwardRoute(int startStage, int endStage){
		//a simple round rubin.
		List<NFVNode> routeList = new ArrayList<NFVNode>();
		for(int i=startStage; i<=endStage; i++){
			Map<String, NFVNode> stageMap = this.workingNodeMaps.get(i);
			if(stageMap.size()>0){
			
				HashSet<String> nonScaleDownSet = new HashSet<String>(stageMap.keySet());
				String[] nonScaleDownArray = nonScaleDownSet.toArray(new String[nonScaleDownSet.size()]);
				String managementIp = nonScaleDownArray[0];
				
				int smallestFlowNum = stageMap.get(managementIp).getActiveFlows();
				
				for(int j=0; j<nonScaleDownArray.length; j++){
					String tmp = nonScaleDownArray[j];
					int flowNum = stageMap.get(tmp).getActiveFlows();
					if( (flowNum<smallestFlowNum) ){
						smallestFlowNum = flowNum;
						managementIp = tmp;
					}
				}
				
				routeList.add(stageMap.get(managementIp));
			}
		}
		return routeList;
	}
	
	public   String getEntryDpid(){
		return this.serviceChainConfig.bridges.get(0);
	}
	
	public   String getDpidForMac(String mac){
		if(this.serviceChainConfig.nVmInterface == 3){
			if(this.entryMacNodeMap.containsKey(mac)){
				return this.entryMacNodeMap.get(mac).vmInstance.bridgeDpidList.get(0);
			}
			else if(this.exitMacNodeMap.containsKey(mac)){
				return this.exitMacNodeMap.get(mac).vmInstance.bridgeDpidList.get(1);
			}
			else {
				return null;
			}
		}
		else{
			return null;
		}
	}
	
	public   boolean macOnRearSwitch(String mac){
		boolean returnVal = false;
		if(this.entryMacNodeMap.containsKey(mac)){
			returnVal = false;
		}
		else if(this.exitMacNodeMap.containsKey(mac)){
			NFVNode node = this.exitMacNodeMap.get(mac);
			if(node.vmInstance.stageIndex == (node.vmInstance.serviceChainConfig.stages.size()-1)){
				returnVal = true;
			}
			else{
				returnVal = false;
			}
		}
		return returnVal;
	}
	
	public   void updateDataNodeStat(String managementIp, ArrayList<String> statList){
		if(this.managementIpNodeMap.containsKey(managementIp)){
			NFVNode node = this.managementIpNodeMap.get(managementIp);
			
			String cpu = statList.get(2);
			String[] cpuStatArray = cpu.trim().split("\\s+");
			float sum = 0;
			for(int i=0; i<cpuStatArray.length; i++){
				sum += Float.parseFloat(cpuStatArray[i]);
			}
			float cpuUsage = (sum-Float.parseFloat(cpuStatArray[3]))*100/sum;
			
			String mem = statList.get(4);
			String[] memStatArray = mem.trim().split("\\s+");
			float memUsage = Float.parseFloat(memStatArray[1])*100/Float.parseFloat(memStatArray[0]);
			memUsage = 100-memUsage;
			
			String interrupt = statList.get(6);
			String[] intStatArray = interrupt.trim().split("\\s+");
			int eth0RecvInt = Integer.parseInt(intStatArray[0]);
			int eth0SendInt = Integer.parseInt(intStatArray[1]);
			int eth1RecvInt = Integer.parseInt(intStatArray[2]);
			int eth1SendInt = Integer.parseInt(intStatArray[3]);
			
			String eth0 = statList.get(8);
			String[] eth0StatArray = eth0.trim().split("\\s+");
			long eth0RecvPkt = Long.parseLong(eth0StatArray[1]);
			long eth0SendPkt = Long.parseLong(eth0StatArray[9]);
			
			String eth1 = statList.get(10);
			String[] eth1StatArray = eth1.trim().split("\\s+");
			long eth1RecvPkt = Long.parseLong(eth1StatArray[1]);
			long eth1SendPkt = Long.parseLong(eth1StatArray[9]);
			
			node.updateNodeProperty(new Float(cpuUsage), new Float(memUsage), 
									new Integer(eth0RecvInt), new Long(eth0RecvPkt), 
									new Integer(eth0SendInt), new Long(eth0SendPkt),
									new Integer(eth1RecvInt), new Long(eth1RecvPkt), 
									new Integer(eth1SendInt), new Long(eth1SendPkt));
		}
	}
	
	public   void updateControlNodeStat(String managementIp, ArrayList<String> statList){
		if(this.managementIpNodeMap.containsKey(managementIp)){
			NFVNode node = this.managementIpNodeMap.get(managementIp);
			
			if(statList.get(1).equals("cpu")){
				
				String cpu = statList.get(2);
				String[] cpuStatArray = cpu.trim().split("\\s+");
				float sum = 0;
				for(int i=0; i<cpuStatArray.length; i++){
					sum += Float.parseFloat(cpuStatArray[i]);
				}
				float cpuUsage = (sum-Float.parseFloat(cpuStatArray[3]))*100/sum;
			
				String mem = statList.get(4);
				String[] memStatArray = mem.trim().split("\\s+");
				float memUsage = Float.parseFloat(memStatArray[1])*100/Float.parseFloat(memStatArray[0]);
				memUsage = 100-memUsage;
			
				String interrupt = statList.get(6);
				String[] intStatArray = interrupt.trim().split("\\s+");
				int eth0RecvInt = Integer.parseInt(intStatArray[0]);
				int eth0SendInt = Integer.parseInt(intStatArray[1]);
				
				String eth0 = statList.get(8);
				String[] eth0StatArray = eth0.trim().split("\\s+");
				long eth0RecvPkt = Long.parseLong(eth0StatArray[1]);
				long eth0SendPkt = Long.parseLong(eth0StatArray[9]);
			
				node.updateNodeProperty(new Float(cpuUsage), new Float(memUsage), 
									    new Integer(eth0RecvInt), new Long(eth0RecvPkt), 
									    new Integer(eth0SendInt), new Long(eth0SendPkt),
									    new Integer(1), new Long(0), 
								 	    new Integer(0), new Long(0));
			}
			else if(statList.get(1).equals("transaction_counter")){
				
				int goodTran = Integer.parseInt(statList.get(3));
				int badTran = Integer.parseInt(statList.get(4));
				int srdSt250ms = Integer.parseInt(statList.get(6));
				int srdLt250ms = Integer.parseInt(statList.get(5))+
								 Integer.parseInt(statList.get(7));
				
				node.updateTranProperty(new Integer(goodTran), 
									    new Integer(badTran), 
									    new Integer(srdSt250ms), 
									    new Integer(srdLt250ms));
			}
		}
	}
	
	public   boolean hasNode(String managementIp){
		if(this.managementIpNodeMap.containsKey(managementIp)){
			return true;
		}
		else{
			return false;
		}
	}
	
	public   Map<String, NFVNode> getManagementIpNodeMap(){
		return this.managementIpNodeMap;
	}
	
	public   NFVNode getNode(String managementIp){
		return this.managementIpNodeMap.get(managementIp);
	}
	
	public   Map<String, NFVNode> getStageMap(int stage){
		return this.workingNodeMaps.get(stage);
	}
	
	public   void setScaleIndicator(int stage, boolean val){
		this.scaleIndicators[stage] = val;
	}
	
	public   boolean getScaleIndicator(int stage){
		return this.scaleIndicators[stage];
	}
	
	public NFVNode randomlyGetWorkingNode(int stageIndex){
		Map<String, NFVNode> nodeMap = this.workingNodeMaps.get(stageIndex);
		HashSet<String> hasSet = new HashSet<String>(nodeMap.keySet());
		String[] array = hasSet.toArray(new String[hasSet.size()]);
		int random = (int )(Math.random() * 100);
		
		return nodeMap.get(array[random%array.length]);
	}
}
