package net.floodlightcontroller.nfvtest.nfvcorestructure;

import net.floodlightcontroller.nfvtest.nfvutils.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class NFVServiceChainStorage {
	//A service chain consists of multiple stages.
	//Each stage has a type associate with it.
	//Each type represents a network function.
	//All middleboxes on the same stage must belong to the stage type.
	
	private final List<String> stageType;
	private final int stageLength;
	
	//They will be concurrently updated by other threads,need protection.
	private final List<Map<String, NFVNode>> stageNodes;
	private final List<Map<Pair<String, String>, NFVLink>> links;
	
	//private Lock linkNodeLock;
	
	NFVServiceChainStorage(List<String> argumentStageType){
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
		
		//linkNodeLock = new Lock();
	}
	
	public synchronized NFVNode getNodeFromChain(int stage, String nodeIndex){
		if(stage>stageLength){
			throw new NFVException("Invalid stage length");
		}
		if(!stageNodes.get(stage).containsKey(nodeIndex)){
			throw new NFVException("Invalid node index");
		}
		return stageNodes.get(stage).get(nodeIndex);
	}
	
	public synchronized void addNodeToChain(int stage, NFVNode node){
		if(stage>stageLength){
			throw new NFVException("Invalid stage length");
		}
		if( (node.getType()!=stageType.get(stage)) ){
			throw new NFVException("Invalid node");
		}
		stageNodes.get(stage).put(node.getNodeIndex(), node);
	}
	
	public synchronized NFVNode deleteNodeFromChain(int stage, String nodeIndex){
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
	public synchronized NFVLink getLinkFromChain(int stage, String srcNodeIndex, String dstNodeIndex){
		if(stage>stageLength){
			throw new NFVException("Invalid stage length");
		}
		Pair<String, String> p = Pair.create(srcNodeIndex, dstNodeIndex);
		if(!links.get(stage).containsKey(p)){
			throw new NFVException("Invalid link");
		}
		return links.get(stage).get(p);
	}
	
	public synchronized void addLinkToChain(int stage, NFVLink l){
		if(stage>stageLength){
			throw new NFVException("Invalid stage length");
		}
		Pair<String, String> p = Pair.create(l.getSrcNode().getNodeIndex(), l.getDstNode().getNodeIndex());
		if(links.get(stage).containsKey(p)){
			throw new NFVException("Link already exist");
		}
		links.get(stage).put(p, l);
	}
	
	public synchronized NFVLink deleteLinkFromChain(int stage, String srcNodeIndex, String dstNodeIndex){
		if(stage>stageLength){
			throw new NFVException("Invalid stage length");
		}
		Pair<String, String> p = Pair.create(srcNodeIndex, dstNodeIndex);
		if(!links.get(stage).containsKey(p)){
			throw new NFVException("Link does not exist");
		}
		return links.get(stage).remove(p);
	}
	
	/*private void acquireLock() throws InterruptedException{
		linkNodeLock.lock();
	}
	
	private void releaseLock(){
		linkNodeLock.unlock();
	}*/
	
	private boolean checkNodeAlive(NFVNode node){
		if( (node.getState()==NFVNode.IDLE)&&(node.getProperty().getFakeProperty() == "zero") ){
			return false;
		}
		else{
			return true;
		}
	}
	
	public synchronized List<NFVNode> pollFlowStatus(){
		List<NFVNode> deletedNode = new ArrayList<NFVNode>();
		for(int i=0; i<stageNodes.size(); i++){
			Map<String, NFVNode> nodeMap = stageNodes.get(i);
			for(String nodeIndex : nodeMap.keySet()){
				NFVNode node = nodeMap.get(nodeIndex);
				if(!checkNodeAlive(node)){
					deletedNode.add(deleteNodeFromChain(node.getStage(), nodeIndex));
				}
			}
		}
		return deletedNode;
	}
}
