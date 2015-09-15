package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;

import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.*;

public class NFVServiceChain {
	
	public final ServiceChainConfig serviceChainConfig;
	private final List<Map<String, NFVNode>> nfvNodeMaps;
	private final Map<String, NFVNode> entryMacNodeMap;
	private final Map<String, NFVNode> exitMacNodeMap;
	private final Map<String, NFVNode> managementIpNodeMap;
	
	public final List<boolean[]> scaleIndicators; 
	public final List<long[]> scaleDownCounter;
	public final List<List<Map<String, Integer>>> scaleDownList;
	
	public final int dcNum;
	private final List<List<Map<String, NFVNode>>> dcNfvNodeMaps;
	
	NFVServiceChain(ServiceChainConfig serviceChainConfig, ControllerConfig controllerConfig){
		this.serviceChainConfig = serviceChainConfig;
		this.nfvNodeMaps = new ArrayList<Map<String, NFVNode>>();
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
		}
		
		this.scaleIndicators = new ArrayList<boolean[]>();
		this.scaleDownCounter = new ArrayList<long[]>();
		this.scaleDownList = new ArrayList<List<Map<String, Integer>>>();
		for(int i=0; i<this.dcNum; i++){
			boolean[] tmpScaleIndicators = new boolean[this.serviceChainConfig.stages.size()];		
			long[] tmpScaleDownCounter  = new long[this.serviceChainConfig.stages.size()];
			for(int j=0; j<this.serviceChainConfig.stages.size(); j++){
				tmpScaleIndicators[j] = false;
				tmpScaleDownCounter[j] = -1;
			}
			this.scaleIndicators.add(tmpScaleIndicators);
			this.scaleDownCounter.add(tmpScaleDownCounter);
			
			List<Map<String, Integer>> tmpScaleDownList = new ArrayList<Map<String, Integer>>();
			for(int j=0; j<this.serviceChainConfig.stages.size(); j++){
				Map<String, Integer> nodeMap = new HashMap<String, Integer>();
				tmpScaleDownList.add(nodeMap);
			}
			this.scaleDownList.add(tmpScaleDownList);
		}
		
		this.dcNum = controllerConfig.dcNum;
		this.dcNfvNodeMaps = new ArrayList<List<Map<String, NFVNode>>>();
		for(int i=0; i<this.dcNum; i++){
			List<Map<String, NFVNode>> nodeMapList = new ArrayList<Map<String, NFVNode>>();
			for(int j=0; j<this.serviceChainConfig.stages.size(); j++){
				Map<String, NFVNode> nodeMap = new HashMap<String, NFVNode>();
				nodeMapList.add(nodeMap);
			}
			this.dcNfvNodeMaps.add(nodeMapList);
		}
	}
	
	public synchronized void addNodeToChain(NFVNode node){
		if((node.vmInstance.serviceChainConfig.name == serviceChainConfig.name)&&
		   (!node.vmInstance.isBufferNode)){
			Map<String, NFVNode> stageMap = this.nfvNodeMaps.get(node.vmInstance.stageIndex);
			if(stageMap.size() == 0){
				stageMap.put(node.getManagementIp(), node);
				this.managementIpNodeMap.put(node.getManagementIp(), node);
				
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
			
			stageMap = this.dcNfvNodeMaps.get(node.vmInstance.hostServerConfig.dcIndex)
					                     .get(node.vmInstance.stageIndex);
			if(stageMap.size() == 0){
				stageMap.put(node.getManagementIp(), node);
			}
			else{
				if(!stageMap.containsKey(node.getManagementIp())){
					stageMap.put(node.getManagementIp(), node);
				}
			}
		}
	}
	
	public synchronized void deleteNodeFromChain(NFVNode node){
		if((node.vmInstance.serviceChainConfig.name == serviceChainConfig.name)&&
			(!node.vmInstance.isBufferNode)){
			Map<String, NFVNode> stageMap = this.nfvNodeMaps.get(node.vmInstance.stageIndex);
			if( (stageMap.containsKey(node.getManagementIp())) ){
				stageMap.remove(node.getManagementIp());
				this.managementIpNodeMap.remove(node.getManagementIp());
				
				if(this.serviceChainConfig.nVmInterface == 3){
					this.entryMacNodeMap.remove(node.vmInstance.macList.get(0));
					this.exitMacNodeMap.remove(node.vmInstance.macList.get(1));
				}
			}
			
			stageMap = this.dcNfvNodeMaps.get(node.vmInstance.hostServerConfig.dcIndex)
                    					 .get(node.vmInstance.stageIndex);

			if(stageMap.containsKey(node.getManagementIp())){
				stageMap.remove(node.getManagementIp());
			}

		}
	}
	
	public synchronized List<NFVNode> forwardRoute(int startDcIndex){
		//a simple round rubin.
		int dcIndex = startDcIndex;
		List<NFVNode> routeList = new ArrayList<NFVNode>();
		for(int i=0; i<this.serviceChainConfig.stages.size(); i++){
			NFVNode node = routeGetNodeWithoutMask(dcIndex, i);
			if(node.getState() == NFVNode.OVERLOAD){
				NFVNode node1 = null;
				int otherDc=0;
				for(; otherDc<this.dcNum; otherDc++){
					if(otherDc==dcIndex){
						continue;
					}
					else{
						node1 = routeGetNodeWithoutMask(otherDc, i);
						if(node1.getState()!=NFVNode.OVERLOAD){
							break;
						}
					}
				}
				
				if(node1.getState()!=NFVNode.OVERLOAD){
					node = node1;
					dcIndex = otherDc;
				}
			}
			routeList.add(node);
		}
		return routeList;
	}
	
	private NFVNode routeGetNodeWithoutMask(int dcIndex, int stage){
		Map<String, Integer> tmpStageScaleDownMap = this.scaleDownList.get(dcIndex).get(stage);
		Map<String, NFVNode> tmpStageMap = this.dcNfvNodeMaps.get(dcIndex).get(stage);
		
		Map<String, Integer> stageScaleDownMap = new HashMap<String, Integer>();
		Map<String, NFVNode> stageMap = new HashMap<String, NFVNode>();
		
		for(String ip : tmpStageMap.keySet()){
			if(!tmpStageMap.get(ip).isMaskOut()){
				stageMap.put(ip, tmpStageMap.get(ip));
			}
		}
		
		if(stageMap.size()==0){
			stageMap = tmpStageMap;
			stageScaleDownMap = tmpStageScaleDownMap;
		}
		else{
			for(String ip: tmpStageScaleDownMap.keySet()){
				if(!tmpStageMap.get(ip).isMaskOut()){
					stageScaleDownMap.put(ip, new Integer(0));
				}
			}
		}
		
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
			
			return stageMap.get(managementIp);
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
				
				return stageMap.get(managementIp);
			}
			else{
				
				String[] nodeArray = stageMap.keySet()
                        .toArray(new String[stageMap.size()]);

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
				 return stageMap.get(managementIp);
			}
		}
	}
	
	/*private NFVNode routeGetNode(int dcIndex, int stage){
		Map<String, Integer> stageScaleDownMap = this.scaleDownList.get(dcIndex).get(stage);
		Map<String, NFVNode> stageMap = this.dcNfvNodeMaps.get(dcIndex).get(stage);
		
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
			
			return stageMap.get(managementIp);
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
				
				return stageMap.get(managementIp);
			}
			else{
				
				String[] nodeArray = stageMap.keySet()
                        .toArray(new String[stageMap.size()]);

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
				 return stageMap.get(managementIp);
			}
		}
	}*/
	
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
	
	public synchronized Map<String, NFVNode> getStageMap(int dcIndex,int stage){
		return this.dcNfvNodeMaps.get(dcIndex).get(stage);
	}
	
	public synchronized void mask(String mIp){
		if(this.managementIpNodeMap.containsKey(mIp)){
			this.managementIpNodeMap.get(mIp).mask();
		}
	}
	
	public synchronized void unmask(String mIp){
		if(this.managementIpNodeMap.containsKey(mIp)){
			this.managementIpNodeMap.get(mIp).unmask();
		}
	}
}
