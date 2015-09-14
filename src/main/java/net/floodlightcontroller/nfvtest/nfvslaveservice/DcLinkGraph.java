package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.ArrayList;
import java.util.List;

import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVNode;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVNode.SimpleSM;
import net.floodlightcontroller.nfvtest.nfvutils.Dijkstra;

import java.util.LinkedList;

public class DcLinkGraph {
 
	public final SimpleSM[][] dcLinkState;
	public final int dcNum; 
	public final Dijkstra dijkstra;
	
	public final int[][] dcLinkGraph;
	
	public DcLinkGraph(int dcNum){
		this.dcLinkState = new SimpleSM[dcNum][dcNum];
		this.dcNum = dcNum;
		this.dijkstra = new Dijkstra();
		
		this.dcLinkGraph = new int[dcNum][dcNum];
		for(int i=0; i<dcNum; i++){
			for(int j=0; j<dcNum; j++){
				this.dcLinkGraph[i][j]=1;
			}
		}
	}
	
	public synchronized void updateDcLinkState(float[][] dcSend, float[][] dcRecv, int dcNum){
		
		if(dcNum!=this.dcNum){
			return;
		}
		
		for(int i=0; i<dcNum; i++){
			for(int j=0; j<dcNum; j++){
				if(i==j){
					continue;
				}
				else{
					//Compare dcSend[i][j] and dcSend[j][i]
					int i2jState = NFVNode.IDLE;
					
					if((dcSend[i][j]>dcRecv[j][i])&&
					   (((dcSend[i][j]-dcRecv[j][i])/dcSend[i][j])>0.01)){
						i2jState = NFVNode.OVERLOAD;
					}
					else{
						i2jState = NFVNode.IDLE;
					}
					
					dcLinkState[i][j].updateTransientState(i2jState);
				}
			}
		}
		
		updateDcLinkGraph();
	}
	
	private synchronized void updateDcLinkGraph(){
		for(int i=0; i<dcNum; i++){
			for(int j=0; j<dcNum; j++){
				if(i>=j){
					continue;
				}
				else{
					if((dcLinkState[i][j].getState()==NFVNode.IDLE)&&
					   (dcLinkState[j][i].getState()==NFVNode.IDLE)){
						this.dcLinkGraph[i][j] = 1;
						this.dcLinkGraph[j][i] = 1;
					}
					else{
						this.dcLinkGraph[i][j] = 0;
						this.dcLinkGraph[j][i] = 0;
					}
				}
			}
		}
	}
	
	public synchronized List<Integer> getPath(int src, int dst){
		if((src>=this.dcNum)||(src<0)){
			return null;
		}
		if((dst>=this.dcNum)||(dst<0)){
			return null;
		}
		
		LinkedList<Integer> queue = new LinkedList<Integer>();
		boolean[] checkedNodes = new boolean[this.dcNum];
		for(int i=0; i<this.dcNum; i++){
			checkedNodes[i] = false;
		}
		
		int[] generatedTree = new int[this.dcNum];
		for(int i=0; i<this.dcNum; i++){
			generatedTree[i] = i;
		}
		
		queue.add(new Integer(src));
		checkedNodes[src] = true;
		
		while(!queue.isEmpty()){
			Integer head = queue.pop();
			int headIndex = head.intValue();
			
			for(int i=0; i<this.dcNum; i++){
				if(i==headIndex){
					continue;
				}
				else{
					if((!checkedNodes[i])&&(this.dcLinkGraph[headIndex][i]==1)){
						generatedTree[i] = headIndex;
						queue.add(new Integer(i));
						checkedNodes[i] = true;
					}
				}
			}
		}
		
		if(generatedTree[dst] == dst){
			List<Integer> returnList = new ArrayList<Integer>();
			returnList.add(new Integer(src));
			returnList.add(new Integer(dst));
			return returnList;
		}
		else{
			List<Integer> returnList = new ArrayList<Integer>();
			returnList.add(new Integer(dst));
			int current = dst;
			while(generatedTree[current]!=current){
				current = generatedTree[current];
				returnList.add(new Integer(current));
			}
			for(int i=0; i<(returnList.size()/2); i++){
				//switch i and returnList.size()-1-i
				Integer tmp = returnList.get(returnList.size()-1-i);
				returnList.set(returnList.size()-1-i, returnList.get(i));
				returnList.set(i, tmp);
			}
			return returnList;
		}
	}
}
