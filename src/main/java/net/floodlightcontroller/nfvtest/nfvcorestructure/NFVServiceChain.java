package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;

import net.floodlightcontroller.nfvtest.nfvutils.Pair;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.*;

public class NFVServiceChain {
	
	public final ServiceChainConfig serviceChainConfig;
	private final List<Map<String, NFVNode>> nfvNodeMaps;
	private final List<String> baseNodeIpList;
	private final int[] rrStore;
	private final Map<String, NFVNode> entryMacNodeMap;
	private final Map<String, NFVNode> exitMacNodeMap;
	private final Map<String, NFVNode> managementIpNodeMap;
	
	private final boolean[] scaleIndicators; 
	
	public final long[] scaleDownCounter;
	public final List<Map<String, Integer>> scaleDownList;
	
	NFVServiceChain(ServiceChainConfig serviceChainConfig){
		this.serviceChainConfig = serviceChainConfig;
		this.nfvNodeMaps = new ArrayList<Map<String, NFVNode>>();
		this.rrStore = new int[this.serviceChainConfig.stages.size()];
		this.baseNodeIpList = new ArrayList<String>(this.serviceChainConfig.stages.size());
		this.managementIpNodeMap = new HashMap<String, NFVNode>();
		
		if(serviceChainConfig.nVmInterface == 3){
			this.entryMacNodeMap = new HashMap<String, NFVNode>();
			this.exitMacNodeMap = new HashMap<String, NFVNode>();
		}
		else{
			this.entryMacNodeMap = null;
			this.exitMacNodeMap = null;
		}
		
		for(int i=0; i<this.serviceChainConfig.stages.size(); i++){
			Map<String, NFVNode> nodeMap = new HashMap<String, NFVNode>();
			this.nfvNodeMaps.add(nodeMap);
			this.rrStore[i] = 0;
		}
		
		scaleIndicators = new boolean[this.serviceChainConfig.stages.size()];
		for(int i=0; i<scaleIndicators.length; i++){
			scaleIndicators[i] = false;
		}
		
		scaleDownList = new ArrayList<Map<String, Integer>>();
		scaleDownCounter = new long[serviceChainConfig.stages.size()];
		for(int i=0; i<this.serviceChainConfig.stages.size(); i++){
			Map<String, Integer> nodeMap = new HashMap<String, Integer>();
			this.scaleDownList.add(nodeMap);
			scaleDownCounter[i] = -1;
		}
	}
	

	public synchronized void addNodeToChain(NFVNode node){
		if(node.vmInstance.serviceChainConfig.name == serviceChainConfig.name){
			Map<String, NFVNode> stageMap = this.nfvNodeMaps.get(node.vmInstance.stageIndex);
			if(stageMap.size() == 0){
				stageMap.put(node.getManagementIp(), node);
				this.managementIpNodeMap.put(node.getManagementIp(), node);
				
				this.baseNodeIpList.set(node.vmInstance.stageIndex, node.getManagementIp());
				
				if(this.serviceChainConfig.nVmInterface == 3){
					this.entryMacNodeMap.put(node.vmInstance.macList.get(0), node);
					this.exitMacNodeMap.put(node.vmInstance.macList.get(1), node);
				}
			}
			else{
				if(!stageMap.containsKey(node.getManagementIp())){
					stageMap.put(node.getManagementIp(), node);
					this.managementIpNodeMap.put(node.getManagementIp(), node);
					
					if(this.serviceChainConfig.nVmInterface == 3){
						this.entryMacNodeMap.put(node.vmInstance.macList.get(0), node);
						this.exitMacNodeMap.put(node.vmInstance.macList.get(1), node);
					}
				}
			}
		}
	}
	
	public synchronized void deleteNodeFromChain(NFVNode node){
		if(node.vmInstance.serviceChainConfig.name == serviceChainConfig.name){
			Map<String, NFVNode> stageMap = this.nfvNodeMaps.get(node.vmInstance.stageIndex);
			if( (stageMap.containsKey(node.getManagementIp())) && 
			    (node.getManagementIp()!=this.baseNodeIpList.get(node.vmInstance.stageIndex)) ){
				stageMap.remove(node.getManagementIp());
				
				this.managementIpNodeMap.remove(node.getManagementIp());
				
				if(this.serviceChainConfig.nVmInterface == 3){
					this.entryMacNodeMap.remove(node.vmInstance.macList.get(0));
					this.exitMacNodeMap.remove(node.vmInstance.macList.get(1));
				}
			}
		}
	}
	
	public synchronized List<NFVNode> forwardRoute(){
		//a simple round rubin.
		List<NFVNode> routeList = new ArrayList<NFVNode>();
		for(int i=0; i<this.nfvNodeMaps.size(); i++){
			Map<String, Integer> stageScaleDownMap = this.scaleDownList.get(i);
			Map<String, NFVNode> stageMap = this.nfvNodeMaps.get(i);
			
			HashSet<String> nonScaleDownSet = new HashSet<String>(stageMap.keySet());
			nonScaleDownSet.removeAll(stageScaleDownMap.keySet());
			
			String[] nonScaleDownArray = nonScaleDownSet.toArray(new String[nonScaleDownSet.size()]);
			
			String managementIp = null;
			
			for(int j=0; j<nonScaleDownArray.length; j++){
				String tmp = nonScaleDownArray[j];
				if(stageMap.get(tmp).getState() != NFVNode.OVERLOAD){
					managementIp = tmp;
					break;
				}
			}
			
			if(managementIp!=null){
				int smallestFlowNum = stageMap.get(managementIp).getActiveFlows();
				
				for(int j=0; j<nonScaleDownArray.length; j++){
					String tmp = nonScaleDownArray[j];
					int flowNum = stageMap.get(tmp).getActiveFlows();
					int state = stageMap.get(tmp).getState();
					if( (flowNum<smallestFlowNum) && (state!=NFVNode.OVERLOAD) ){
						smallestFlowNum = flowNum;
						managementIp = tmp;
					}
				}
				
				routeList.add(stageMap.get(managementIp));
			}
			else{
				if(stageScaleDownMap.size()>0){
					String[] scaleDownArray = stageScaleDownMap.keySet()
						                     .toArray(new String[stageScaleDownMap.size()]);
					
					managementIp = scaleDownArray[0];
					int smallestFlowNum = stageMap.get(managementIp).getActiveFlows();
					
					for(int j=0; j<scaleDownArray.length; j++){
						String tmp = scaleDownArray[j];
						int flowNum = stageMap.get(tmp).getActiveFlows();
						if(flowNum<smallestFlowNum){
							smallestFlowNum = flowNum;
							managementIp = tmp;
						}
					}
					
					routeList.add(stageMap.get(managementIp));
				}
				else{
					String[] nodeArray = stageMap.keySet()
										 .toArray(new String[stageScaleDownMap.size()]);
					
					managementIp = nodeArray[0];
					int smallestFlowNum = stageMap.get(managementIp).getActiveFlows();
					
					for(int j=0; j<nodeArray.length; j++){
						String tmp = nodeArray[j];
						int flowNum = stageMap.get(tmp).getActiveFlows();
						if(flowNum<smallestFlowNum){
							smallestFlowNum = flowNum;
							managementIp = tmp;
						}
					}
					
					routeList.add(stageMap.get(managementIp));
				}
				
			}
		}
		return routeList;
	}
	
	public synchronized String getEntryDpid(){
		Map<String, NFVNode> nodeMap = this.nfvNodeMaps.get(0);
		NFVNode baseNode = nodeMap.get(this.baseNodeIpList.get(0));
		return baseNode.vmInstance.bridgeDpidList.get(0);
	}
	
	public synchronized String getDpidForMac(String mac){
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
	
	public synchronized boolean macOnRearSwitch(String mac){
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
	
	public synchronized boolean hasNode(String managementIp){
		if(this.managementIpNodeMap.containsKey(managementIp)){
			return true;
		}
		else{
			return false;
		}
	}
	
	public synchronized Map<String, NFVNode> getManagementIpNodeMap(){
		return this.managementIpNodeMap;
	}
	
	public synchronized NFVNode getNode(String managementIp){
		return this.managementIpNodeMap.get(managementIp);
	}
	
	public synchronized Map<String, NFVNode> getStageMap(int stage){
		return this.nfvNodeMaps.get(stage);
	}
	
	public synchronized void setScaleIndicator(int stage, boolean val){
		this.scaleIndicators[stage] = val;
	}
	
	public synchronized int getNodeWithLeastFlows(int stageIndex, 
			             ArrayList<Pair<String, Integer>> flowNumArray){
		
		String baseNodeIp = this.baseNodeIpList.get(stageIndex);
		
		if(flowNumArray.size() == 0){
			return -1;
		}
		if((flowNumArray.size() == 1)&&(flowNumArray.get(0).first.equals(baseNodeIp))){
			return -1;
		}
		
		int smallestFlowNum = 0;
		int smallestIndex = 0;
		
		for(int i=0; i<flowNumArray.size(); i++){
			String managementIp = flowNumArray.get(i).first;
			int flowNum = flowNumArray.get(i).second.intValue();
			
			if(!managementIp.equals(baseNodeIp)){
				smallestFlowNum = flowNum;
				smallestIndex = i;
				break;
			}
		}
		
		for(int i=0; i<flowNumArray.size(); i++){
			String managementIp = flowNumArray.get(i).first;
			int flowNum = flowNumArray.get(i).second.intValue();
			
			if( (flowNum<smallestFlowNum) &&
			    (!managementIp.equals(baseNodeIp)) ){
				smallestFlowNum = flowNum;
				smallestIndex = i;
			}
		}
		
		return smallestIndex;
	}
	
	public synchronized ArrayList<Pair<String, Integer>> getFlowNumArray(int stageIndex){
		Map<String,NFVNode> nodeMap = this.nfvNodeMaps.get(stageIndex);
		ArrayList<Pair<String, Integer>> flowNumArray = new ArrayList<Pair<String, Integer>>();
		
		for(String ip : nodeMap.keySet()){
			NFVNode node = nodeMap.get(ip);
			flowNumArray.add(new Pair<String, Integer>(node.getManagementIp(), 
					         new Integer(node.getActiveFlows())));
		}
		
		return flowNumArray;
	}
	
	public synchronized boolean getScaleIndicator(int stage){
		return this.scaleIndicators[stage];
	}
}
