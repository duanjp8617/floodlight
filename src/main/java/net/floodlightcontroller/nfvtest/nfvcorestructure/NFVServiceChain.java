package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.*;

public class NFVServiceChain {
	
	public final ServiceChainConfig serviceChainConfig;
	private final List<Map<String, NFVNode>> nfvNodeMaps;
	private final List<String> baseNodeMacList;
	private final int[] rrStore;
	private final Map<String, NFVNode> entryMacNodeMap;
	private final Map<String, NFVNode> exitMacNodeMap;
	
	NFVServiceChain(ServiceChainConfig serviceChainConfig){
		this.serviceChainConfig = serviceChainConfig;
		this.nfvNodeMaps = new ArrayList<Map<String, NFVNode>>();
		this.rrStore = new int[this.serviceChainConfig.stages.size()];
		this.baseNodeMacList = new ArrayList<String>(this.serviceChainConfig.stages.size());
		
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
	}
	

	public synchronized void addNodeToChain(NFVNode node){
		if(node.vmInstance.serviceChainConfig.name == serviceChainConfig.name){
			Map<String, NFVNode> stageMap = this.nfvNodeMaps.get(node.vmInstance.stageIndex);
			if(stageMap.size() == 0){
				stageMap.put(node.getManagementMac(), node);
				this.baseNodeMacList.add(node.vmInstance.stageIndex, node.getManagementMac());
				
				if(this.serviceChainConfig.nVmInterface == 3){
					this.entryMacNodeMap.put(node.vmInstance.macList.get(0), node);
					this.exitMacNodeMap.put(node.vmInstance.macList.get(1), node);
				}
			}
			else{
				if(!stageMap.containsKey(node.getManagementMac())){
					stageMap.put(node.getManagementMac(), node);
					
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
			if( (stageMap.containsKey(node.getManagementMac())) && 
			    (node.getManagementMac()!=this.baseNodeMacList.get(node.vmInstance.stageIndex)) ){
				stageMap.remove(node.getManagementMac());
				
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
			Map<String, NFVNode> stageMap = this.nfvNodeMaps.get(i);
			if(this.rrStore[i]>=stageMap.size()){
				this.rrStore[i]=0;
			}
			String[] keyArray = stageMap.keySet().toArray(new String[stageMap.keySet().size()]);
			routeList.add(stageMap.get(keyArray[this.rrStore[i]]));
			this.rrStore[i] = (this.rrStore[i]+1)%stageMap.size();
		}
		return routeList;
	}
	
	public synchronized String getEntryDpid(){
		Map<String, NFVNode> nodeMap = this.nfvNodeMaps.get(0);
		NFVNode baseNode = nodeMap.get(this.baseNodeMacList.get(0));
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
}
