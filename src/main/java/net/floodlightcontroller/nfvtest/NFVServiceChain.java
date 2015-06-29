package net.floodlightcontroller.nfvtest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public abstract class NFVServiceChain {
	//A service chain consists of multiple stages.
	//Each stage has a type associate with it.
	//Each type represents a network function.
	//All middleboxes on the same stage must belong to the stage type.
	
	private List<String> stageType;
	private List<Map<String, NFVNode>> stageNodes;
	private int stageLength;
	private List<Map<Pair<String, String>, NFVLink>> links;
	
	NFVServiceChain(List<String> argumentStageType){
		stageLength = argumentStageType.size();
		
		stageType = new ArrayList<String>(argumentStageType);
		
		stageNodes = new ArrayList<Map<String,NFVNode>>(stageLength);
		links = new ArrayList<Map<Pair<String, String>, NFVLink>>(stageLength);
		for(int i=0; i<stageLength; i++){
			Map<String,NFVNode> ref = new HashMap<String,NFVNode>();
			stageNodes.add(ref);
			Map<Pair<String, String>, NFVLink> ref1 = new HashMap<Pair<String, String>, NFVLink>();
			links.add(ref1);
		}
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
	
	public void addNodeToChain(int stage, NFVNode node){
		if(stage>stageLength){
			throw new NFVException("Invalid stage length");
		}
		if( (node.getType()!=stageType.get(stage))||(!node.getIsInitialized()) ){
			throw new NFVException("Invalid node");
		}
		stageNodes.get(stage).put(node.getNodeIndex(), node);
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
	
	//srcNodeIndex belongs to previous stage, dstNodeIndex belongs to afterward stage.
	public NFVLink getLinkFromChain(int stage, String srcNodeIndex, String dstNodeIndex){
		if(stage>stageLength){
			throw new NFVException("Invalid stage length");
		}
		Pair<String, String> p = Pair.create(srcNodeIndex, dstNodeIndex);
		if(!links.get(stage).containsKey(p)){
			throw new NFVException("Invalid link");
		}
		return links.get(stage).get(p);
	}
	
	public void addLinkToChain(int stage, NFVLink l){
		if(stage>stageLength){
			throw new NFVException("Invalid stage length");
		}
		Pair<String, String> p = Pair.create(l.getSrcNode().getNodeIndex(), l.getDstNode().getNodeIndex());
		if(links.get(stage).containsKey(p)){
			throw new NFVException("Link already exist");
		}
		links.get(stage).put(p, l);
	}
	
	public NFVLink deleteLinkFromChain(int stage, String srcNodeIndex, String dstNodeIndex){
		if(stage>stageLength){
			throw new NFVException("Invalid stage length");
		}
		Pair<String, String> p = Pair.create(srcNodeIndex, dstNodeIndex);
		if(!links.get(stage).containsKey(p)){
			throw new NFVException("Link does not exist");
		}
		return links.get(stage).remove(p);
	}
}
