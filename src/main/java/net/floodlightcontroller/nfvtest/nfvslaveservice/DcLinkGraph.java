package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.List;

import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVNode;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVNode.SimpleSM;
import net.floodlightcontroller.nfvtest.nfvutils.Dijkstra;



public class DcLinkGraph {
 
	public final SimpleSM[][] dcLinkState;
	public final int dcNum; 
	public final Dijkstra dijkstra;
	
	public final int[][] dcLinkGraph;
	
	DcLinkGraph(int dcNum){
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
	
	public void updateDcLinkState(float[][] dcSend, float[][] dcRecv, int dcNum){
		
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
					}
					else{
						this.dcLinkGraph[i][j] = 0;
					}
				}
			}
		}
	}
	
	public synchronized List<Integer> getPath(int src, int dst){
		this.dijkstra.contructGraph(this.dcLinkGraph, this.dcNum);
		return this.dijkstra.computePath(src, dst);
	}
	
}
