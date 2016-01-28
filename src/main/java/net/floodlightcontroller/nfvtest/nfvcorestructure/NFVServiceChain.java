package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.types.DatapathId;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.*;

public class NFVServiceChain {
	
	public final ServiceChainConfig serviceChainConfig;
	private final Map<String, NFVNode> managementIpNodeMap;
	public final List<Map<String, NFVNode>> nodeMap;

	private final boolean[] scaleIndicators;
	
	private final List<Map<String, NFVNode>> workingNodeMaps;
	private final List<Deque<NFVNode>>       bufferNodeQueues;
	public final Map<String, NFVNode> destroyNodeMap;
	
	private int scalingInterval;
	private int maximumBufferingInterval = 5;
	
	private int dpPaths[][][];
	private int previousDpPaths[][][];
	private int nextDpPaths[][][];
	
	private final Map<DatapathId, Map<Integer, NFVNode>> dpidNodeExitPortMap;
	
	NFVServiceChain(ServiceChainConfig serviceChainConfig){
		this.serviceChainConfig = serviceChainConfig;
		this.managementIpNodeMap = new HashMap<String, NFVNode>();
		this.nodeMap = new ArrayList<Map<String, NFVNode>>();
		
		this.workingNodeMaps = new ArrayList<Map<String, NFVNode>>();
		this.bufferNodeQueues = new ArrayList<Deque<NFVNode>>();
		for(int i=0; i<this.serviceChainConfig.stages.size(); i++){
			Map<String, NFVNode> workingNodeMap = new HashMap<String, NFVNode>();
			this.workingNodeMaps.add(workingNodeMap);
			
			Deque<NFVNode> bufferNodeQueue = new LinkedList<NFVNode>();
			this.bufferNodeQueues.add(bufferNodeQueue);
			
			Map<String, NFVNode> totalNodeMap = new HashMap<String, NFVNode>();
			this.nodeMap.add(totalNodeMap);
		}
		
		destroyNodeMap = new HashMap<String, NFVNode>();
		
		scaleIndicators = new boolean[this.serviceChainConfig.stages.size()];
		for(int i=0; i<scaleIndicators.length; i++){
			scaleIndicators[i] = false;
		}
		
		this.dpidNodeExitPortMap = new HashMap<DatapathId, Map<Integer, NFVNode>>();
	
		this.scalingInterval = 0;
		this.dpPaths = null;
		this.previousDpPaths = null;
		this.nextDpPaths = null;
	}
	
	public synchronized int getScalingInterval(){
		return (this.scalingInterval)%4;
	}
	
	public synchronized int[] getPreviousDpPaths(int srcDcIndex, int dstDcIndex){
		return this.previousDpPaths[srcDcIndex][dstDcIndex];
	}
	
	public synchronized int[] getCurrentDpPaths(int srcDcIndex, int dstDcIndex){
		return this.dpPaths[srcDcIndex][dstDcIndex];
	}
	
	public synchronized int[] getNextDpPaths(int srcDcIndex, int dstDcIndex){
		return this.nextDpPaths[srcDcIndex][dstDcIndex];
	}
	
	public synchronized  void addNextDpPaths(int[][][] nextDpPaths){
		if(this.serviceChainConfig.nVmInterface == 3){
			this.nextDpPaths = nextDpPaths;
		}
	}
	
	public synchronized void addScalingInterval(){
		if(this.serviceChainConfig.nVmInterface == 3){
			this.scalingInterval += 1;
			previousDpPaths = dpPaths;
			dpPaths = nextDpPaths;
		}
	}
	
	public synchronized int[] getProvision(){
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
	public synchronized int bqSize(int stageIndex){
		return bufferNodeQueues.get(stageIndex).size();
	}
	
	public synchronized void addToBqRear(NFVNode node){
		int stageIndex = node.vmInstance.stageIndex;
		node.setScalingInterval(this.scalingInterval);
		bufferNodeQueues.get(stageIndex).addLast(node);
	}
	
	public synchronized NFVNode removeFromBqRear(int stageIndex){
		NFVNode node = bufferNodeQueues.get(stageIndex).pollLast();
		if(node!=null)
			node.setScalingInterval(-1);
		return node;
	}
	
	public synchronized NFVNode removeFromBqHead(int stageIndex){
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
	public synchronized void addWorkingNode(NFVNode node){
		if(node.vmInstance.serviceChainConfig.name.equals(serviceChainConfig.name)){
			Map<String, NFVNode> stageMap = this.workingNodeMaps.get(node.vmInstance.stageIndex);
			if(!stageMap.containsKey(node.getManagementIp())){
				stageMap.put(node.getManagementIp(), node);
			}
		}
	}
	
	public synchronized void removeWorkingNode(NFVNode node){
		if(node.vmInstance.serviceChainConfig.name.equals(serviceChainConfig.name)){
			Map<String, NFVNode> stageMap = this.workingNodeMaps.get(node.vmInstance.stageIndex);
			if( (stageMap.containsKey(node.getManagementIp())) ){
				stageMap.remove(node.getManagementIp());
			}
		}
	}
	
	//The following 2 functions are public interfaces for manipulating the destroy
	//node map
	public synchronized void addDestroyNode(NFVNode node){
		if(!destroyNodeMap.containsKey(node.getManagementIp())){
			destroyNodeMap.put(node.getManagementIp(), node);
		}
	}
	
	//The following 2 function are used to to add the node to the service chain.
	//once the nodes are added to the service chain, we will record the node in
	//possibly 3 maps.
	public synchronized void addToServiceChain(NFVNode node){
		if(!this.managementIpNodeMap.containsKey(node.getManagementIp())){
			this.managementIpNodeMap.put(node.getManagementIp(), node);
			this.nodeMap.get(node.vmInstance.stageIndex).put(node.getManagementIp(), node);
			
			if(this.serviceChainConfig.nVmInterface == 3){
				DatapathId exitSwitchDpid = DatapathId.of(node.getBridgeDpid(1));
				if(!this.dpidNodeExitPortMap.containsKey(exitSwitchDpid)){
					this.dpidNodeExitPortMap.put(exitSwitchDpid, new HashMap<Integer, NFVNode>());
					int exitPortNum = node.vmInstance.getPort(1);
					this.dpidNodeExitPortMap.get(exitSwitchDpid).put(new Integer(exitPortNum), node);
				}
				else{
					int exitPortNum = node.vmInstance.getPort(1);
					this.dpidNodeExitPortMap.get(exitSwitchDpid).put(new Integer(exitPortNum), node);
				}
			}
		}
	}
	
	public synchronized void removeFromServiceChain(NFVNode node){
		
		if(this.managementIpNodeMap.containsKey(node.getManagementIp())){
			this.managementIpNodeMap.remove(node.getManagementIp());
			this.nodeMap.get(node.vmInstance.stageIndex).remove(node.getManagementIp());
			
			if(this.serviceChainConfig.nVmInterface == 3){
				DatapathId exitSwitchDpid = DatapathId.of(node.getBridgeDpid(1));
				int exitPortNum = node.vmInstance.getPort(1);
				this.dpidNodeExitPortMap.get(exitSwitchDpid).remove(new Integer(exitPortNum));
			}
		}
	}
	
	public synchronized boolean exitFromNode(DatapathId dpid, int portNum){
		if(this.dpidNodeExitPortMap.containsKey(dpid)){
			if(this.dpidNodeExitPortMap.get(dpid).containsKey(new Integer(portNum))){
				return true;
			}
			else{
				return false;
			}
		}
		else{
			return false;
		}
	}
	
	//this function returns an NFVNode whose state is not overload
	public synchronized NFVNode getNormalWorkingNode(int stage){
		Map<String, NFVNode> stageMap = workingNodeMaps.get(stage);
		
		for(String key : stageMap.keySet()){
			NFVNode node = stageMap.get(key);
			if(node.getState() != NFVNode.OVERLOAD){
				return node;
			}
		}
		
		return null;
	}
	
	public synchronized List<NFVNode> forwardRoute(int startStage, int endStage){
		//a simple round rubin.
		List<NFVNode> routeList = new ArrayList<NFVNode>();
		for(int i=startStage; i<=endStage; i++){
			Map<String, NFVNode> stageMap = this.workingNodeMaps.get(i);
			if(stageMap.size()>0){
			
				HashSet<String> nonScaleDownSet = new HashSet<String>(stageMap.keySet());
				String[] nonScaleDownArray = nonScaleDownSet.toArray(new String[nonScaleDownSet.size()]);
				String managementIp = nonScaleDownArray[0];
				
				long smallestFlowNum = stageMap.get(managementIp).getCurrentRecvPkt();
				
				for(int j=0; j<nonScaleDownArray.length; j++){
					String tmp = nonScaleDownArray[j];
					long flowNum = stageMap.get(tmp).getCurrentRecvPkt();
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
	
	public synchronized void updateDataNodeStat(String managementIp, ArrayList<String> statList){
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
			
			String eth0 = statList.get(8);
			String[] eth0StatArray = eth0.trim().split("\\s+");
			long eth0RecvPkt = Long.parseLong(eth0StatArray[1]);
			long eth0RecvBdw = Long.parseLong(eth0StatArray[0]);
			eth0RecvBdw = (eth0RecvBdw/(1024*1024))*8;
			
			String eth1 = statList.get(10);
			String[] eth1StatArray = eth1.trim().split("\\s+");
			long eth1SendPkt = Long.parseLong(eth1StatArray[9]);
			
			node.updateNodeProperty(new Float(cpuUsage), new Long(eth0RecvBdw), new Long(eth0RecvPkt), new Long(eth1SendPkt));
		}
	}
	
	public synchronized void updateControlNodeStat(String managementIp, ArrayList<String> statList){
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
				
				String eth0 = statList.get(8);
				String[] eth0StatArray = eth0.trim().split("\\s+");
				long eth0RecvPkt = Long.parseLong(eth0StatArray[1]);
				long eth0RecvBdw = Long.parseLong(eth0StatArray[0]);
				eth0RecvBdw = (eth0RecvBdw/(1024*1024))*8;
				
				node.updateNodeProperty(new Float(cpuUsage), new Long(eth0RecvBdw), new Long(eth0RecvPkt), new Long(0));
			}
		}
	}
	
	public synchronized boolean hasNode(String managementIp){
		if(this.managementIpNodeMap.containsKey(managementIp)){
			return true;
		}
		else{
			return false;
		}
	}
	
	public synchronized NFVNode getNode(String managementIp){
		return this.managementIpNodeMap.get(managementIp);
	}
	
	public synchronized Map<String, NFVNode> getStageMap(int stage){
		return this.workingNodeMaps.get(stage);
	}
	
	public synchronized void setScaleIndicator(int stage, boolean val){
		this.scaleIndicators[stage] = val;
	}
	
	public synchronized boolean getScaleIndicator(int stage){
		return this.scaleIndicators[stage];
	}
	
	public synchronized NFVNode randomlyGetWorkingNode(int stageIndex){
		Map<String, NFVNode> nodeMap = this.workingNodeMaps.get(stageIndex);
		HashSet<String> hasSet = new HashSet<String>(nodeMap.keySet());
		String[] array = hasSet.toArray(new String[hasSet.size()]);
		int random = (int )(Math.random() * 100);
		
		return nodeMap.get(array[random%array.length]);
	}
}