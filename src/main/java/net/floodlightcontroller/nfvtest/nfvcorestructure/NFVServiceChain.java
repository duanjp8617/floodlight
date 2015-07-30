package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.*;

public class NFVServiceChain {
	
	public final ServiceChainConfig serviceChainConfig;
	private final List<Map<String, NFVNode>> nfvNodeLists;
	public final String ingressBridgeDpid;
	public final String egressBridgeDpid;
	public final String ingressIp;
	public final String egressIp;
	
	private final int[] rrStore;
	
	
	
	NFVServiceChain(ServiceChainConfig serviceChainConfig,
			        String ingressBridgeDpid,
			        String egressBridgeDpid,
			        String ingressIp,
			        String egressIp){
		this.serviceChainConfig = serviceChainConfig;
		this.nfvNodeLists = new ArrayList<Map<String, NFVNode>>();
		this.ingressBridgeDpid = ingressBridgeDpid;
		this.egressBridgeDpid = egressBridgeDpid;
		this.ingressIp = ingressIp;
		this.egressIp = egressIp;
		
		this.rrStore = new int[this.serviceChainConfig.stages.size()];
		
		for(int i=0; i<this.serviceChainConfig.stages.size(); i++){
			Map<String, NFVNode> nodeMap = new HashMap<String, NFVNode>();
			this.nfvNodeLists.add(nodeMap);
			
			this.rrStore[i] = 0;
		}
		
		
	}
	
	public synchronized void addNodeToChain(NFVNode node){
		if(node.vmInstance.serviceChainConfig.name == serviceChainConfig.name){
			Map<String, NFVNode> stageMap = this.nfvNodeLists.get(node.vmInstance.stageIndex);
			stageMap.put(node.getMacAddress(node.getNumInterfaces()-1), node);
		}
	}
	
	public synchronized void deleteNodeFromChain(NFVNode node){
		if(node.vmInstance.serviceChainConfig.name == serviceChainConfig.name){
			Map<String, NFVNode> stageMap = this.nfvNodeLists.get(node.vmInstance.stageIndex);
			if(stageMap.containsKey(node.getMacAddress(node.getNumInterfaces()-1))){
				stageMap.remove(node.getMacAddress(node.getNumInterfaces()-1));
			}
		}
	}
	
	public synchronized List<NFVNode> forwardRoute(){
		//a simple round rubin.
		List<NFVNode> routeList = new ArrayList<NFVNode>();
		for(int i=0; i<this.nfvNodeLists.size(); i++){
			Map<String, NFVNode> stageMap = this.nfvNodeLists.get(i);
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
