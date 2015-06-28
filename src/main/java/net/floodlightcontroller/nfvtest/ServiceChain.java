package net.floodlightcontroller.nfvtest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public abstract class ServiceChain {
	//A service chain consists of multiple stages.
	//Each stage has a type associate with it.
	//Each type represents a network function.
	//All middleboxes on the same stage must belong to the stage type.
	
	private List<String> stageType;
	private List<Map<String, NFVNode>> stageNodes;
	private int stageLength;
	
	ServiceChain(List<String> argumentStageType){
		stageLength = argumentStageType.size();
		
		stageType = new ArrayList<String>(argumentStageType);
		
		stageNodes = new ArrayList<Map<String,NFVNode>>(stageLength);
		for(int i=0; i<stageLength; i++){
			Map<String,NFVNode> ref = new HashMap<String,NFVNode>();
			stageNodes.add(ref);
		}
	}
	
	public void addNodeToStage(NFVNode node, int stage){
		if(stage>stageLength){
			throw new NFVException("Invalid stage length");
		}
		if( (node.getType()!=stageType.get(stage))||(!node.getIsInitialized()) ){
			throw new NFVException("Invalid node");
		}
		stageNodes.get(stage).put(node.getNodeIndex(), node);
	}
	
	public NFVNode getNodeFromChain(int stage, String nodeIndex){
		if(stage>stageLength){
			throw new NFVException("Invalid stage length");
		}
		if(!stageNodes.get(stage).containsKey(nodeIndex)){
			throw new NFVException("Invalid node index");
		}
		return stageNodes.get(stage).get(nodeIndex);
	}
	
	public NFVNode deleteNodeFromChain(int stage, String nodeIndex){
		if(stage>stageLength){
			throw new NFVException("Invalid stage length");
		}
		NFVNode node = stageNodes.get(stage).remove(nodeIndex);
		if(node == null){
			throw new NFVException("Invalid node index");
		}
		return node;
	}
	
}
