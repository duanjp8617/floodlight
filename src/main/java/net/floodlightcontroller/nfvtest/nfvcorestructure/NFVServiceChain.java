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
	
	NFVServiceChain(ServiceChainConfig serviceChainConfig){
		this.serviceChainConfig = serviceChainConfig;
		this.nfvNodeMaps = new ArrayList<Map<String, NFVNode>>();
		this.rrStore = new int[this.serviceChainConfig.stages.size()];
		this.baseNodeMacList = new ArrayList<String>();
		
		for(int i=0; i<this.serviceChainConfig.stages.size(); i++){
			Map<String, NFVNode> nodeMap = new HashMap<String, NFVNode>();
			this.nfvNodeMaps.add(nodeMap);
			this.rrStore[i] = 0;
		}
	}
	
	public synchronized void setBaseNodeMacList(List<String> macList){
		for(String mac : macList){
			this.baseNodeMacList.add(mac);
		}
	}
	
	public synchronized void addNodeToChain(NFVNode node){
		if(node.vmInstance.serviceChainConfig.name == serviceChainConfig.name){
			Map<String, NFVNode> stageMap = this.nfvNodeMaps.get(node.vmInstance.stageIndex);
			if(!stageMap.containsKey(node.getManagementMac())){
				stageMap.put(node.getManagementMac(), node);
			}
		}
	}
	
	public synchronized void deleteNodeFromChain(NFVNode node){
		if(node.vmInstance.serviceChainConfig.name == serviceChainConfig.name){
			Map<String, NFVNode> stageMap = this.nfvNodeMaps.get(node.vmInstance.stageIndex);
			if(stageMap.containsKey(node.getManagementMac())){
				stageMap.remove(node.getManagementMac());
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
}
