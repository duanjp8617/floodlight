package net.floodlightcontroller.nfvtest;

public class NFVLink {
	//The NFV service chain is a complete bipartite graph.
	//So the NFV link could be defined as a simple class.
	private final NFVNode srcNode;
	private final NFVNode dstNode;
	
	//The link is a doulbe link, meaning that it contains the 
	//property of both src->dst link and dst->src link.
	
	public class NFVLinkProperty{
		String property;
		NFVLinkProperty(String p){
			property = p;
		}
	}
	
	private final NFVLinkProperty forwardProperty;
	private final NFVLinkProperty backwardProperty;
	
	NFVLink(NFVNode s, NFVNode d){
		srcNode = s;
		dstNode = d;
		forwardProperty = new NFVLinkProperty("p1");
		backwardProperty = new NFVLinkProperty("p2");
	}

	public NFVNode getSrcNode() {
		return srcNode;
	}

	public NFVNode getDstNode() {
		return dstNode;
	}

	public NFVLinkProperty getForwardProperty() {
		return forwardProperty;
	}

	public NFVLinkProperty getBackwardProperty() {
		return backwardProperty;
	}

}
