package net.floodlightcontroller.nfvtest;

import java.util.Collection;
import java.util.Map;
import java.lang.String;

public abstract class ServiceChain {
	Collection<String> stages;
	Map<String, Collection<NFVNode>> nodeMap;
	
	void addStage(String argumentStage){
		stages.add(argumentStage);
	}
	
	public enum NodeOnStage{
		INCORRECTSTAGE,
		NONODE,
		HASNODE
	}
	
	NodeOnStage isNodeOnStage(String argumentStage, NFVNode argumentNode){
		if(!nodeMap.containsKey(argumentStage)){
			return NodeOnStage.INCORRECTSTAGE;
		}
		else{
			Collection<NFVNode> stageCollection = 
					nodeMap.get(argumentStage);
			for( NFVNode node : stageCollection ){
				if(node.getUUID() == argumentNode.getUUID()){
					return NodeOnStage.HASNODE;
				}
			}
		}
		return NodeOnStage.NONODE;
	}
	
	NodeOnStage addNode(String argumentStage, NFVNode argumentNode){
		NodeOnStage returnVal = isNodeOnStage(argumentStage, argumentNode);
		if(returnVal == NodeOnStage.NONODE){
			nodeMap.get(argumentStage).add(argumentNode);
		}
		return returnVal;
	}
	
	NodeOnStage deleteNode(String argumentStage, NFVNode argumentNode){
		NFVNode nodeToDelete = argumentNode;
		NodeOnStage returnVal = isNodeOnStage(argumentStage, nodeToDelete);
		if(returnVal == NodeOnStage.HASNODE){
			nodeMap.get(argumentStage).remove(nodeToDelete);
		}
		return returnVal;
	}
}
