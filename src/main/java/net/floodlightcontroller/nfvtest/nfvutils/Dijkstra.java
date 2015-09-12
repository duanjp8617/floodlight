package net.floodlightcontroller.nfvtest.nfvutils;

import java.util.List;
import java.util.ArrayList;


public class Dijkstra {
	class Edge {
		public final Vertex v1;
		public final Vertex v2;
		public final int distance;
		public Edge(Vertex v1, Vertex v2, int distance){
			this.v1 = v1;
			this.v2 = v2;
			this.distance = distance;
		}
	}
	
	class Vertex {
		public int minDistance;
		public Vertex previous;
		
		public ArrayList<Edge> edges;
		public final int index;
		public Vertex(int index){
			this.index = index;
			this.edges = new ArrayList<Edge>();
		}
	}
	
	
	private final ArrayList<Vertex> vertexList;
	
	public Dijkstra(){
		this.vertexList = new ArrayList<Vertex>();
	}
	
	public void contructGraph(int[][] graph, int size){
		this.vertexList.clear();
		for(int i=0; i<size; i++){
			Vertex vertex = new Vertex(i);
			this.vertexList.add(vertex);
		}
		
		for(int i=0; i<size; i++){
			Vertex v1 = this.vertexList.get(i);
			for(int j=0; j<size; j++){
				if(i>=j){
					continue;
				}
				else{
					Vertex v2 = this.vertexList.get(j);
					if(graph[i][j]==1){
						Edge edge = new Edge(v1, v2, 1);
						v1.edges.add(edge);
						v2.edges.add(edge);
					}
				}
			}
		}
	}
	
	public List<Integer> computePath(int src, int dst){
		boolean[] checkedList = new boolean[this.vertexList.size()];
		int checkedNum = 0;
		
		for(int i=0; i<this.vertexList.size(); i++){
			this.vertexList.get(i).minDistance = 50000;
			this.vertexList.get(i).previous = null;
			checkedList[i] = false;
		}
		this.vertexList.get(src).minDistance = 0;
		
		Vertex current = this.vertexList.get(src);
		
		while(checkedNum<this.vertexList.size()){
			
			for(int i=0; i<current.edges.size(); i++){
				Edge e = current.edges.get(i);
				Vertex other = (e.v1.index==current.index)?e.v2:e.v1;
				
				if(!checkedList[other.index]){
					int throughCurrent = current.minDistance+e.distance;
					if(throughCurrent<other.minDistance){
						other.minDistance = throughCurrent;
						other.previous = current;
					}
				}
			}
			
			checkedList[current.index] = true;
			checkedNum+=1;
			
			if(checkedList[dst]==true){
				break;
			}
			else{
				int smallestIndex=-1;
				int smallestValue=50000;
				
				for(int i=0; i<this.vertexList.size(); i++){
					if( (smallestValue>=this.vertexList.get(i).minDistance)&&
					    (!checkedList[this.vertexList.get(i).index]) ){
						smallestValue = this.vertexList.get(i).minDistance;
						smallestIndex = i;
					}
				}
				
				if(smallestIndex!=-1){
					current = this.vertexList.get(smallestIndex);
				}
				else{
					break;
				}
			}
		}
		
		List<Integer> path = new ArrayList<Integer>();
		
		if(this.vertexList.get(dst).minDistance<50000){
			Vertex v = this.vertexList.get(dst);
			path.add(new Integer(v.index));
			while(v.index!=src){
				v=this.vertexList.get(v.previous.index);
				path.add(new Integer(v.index));
			}
		}
		else{
			path.add(new Integer(dst));
			path.add(new Integer(src));
		}
		
		return path;
	}
}