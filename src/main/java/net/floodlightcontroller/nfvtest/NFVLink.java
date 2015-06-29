package net.floodlightcontroller.nfvtest;

public class NFVLink {
	//The NFV service chain is a complete bipartite graph.
	//So the NFV link could be defined as a simple class.
	private NFVNode srcNode;
	private NFVNode dstNode;
	
	//The link is a doulbe link, meaning that it contains the 
	//property of both src->dst link and dst->src link.
	
	public class NFVLinkProperty{
		String property;
		NFVLinkProperty(String p){
			property = p;
		}
	}
	
	NFVLinkProperty forwardProperty;
	NFVLinkProperty backwardProperty;
	
	NFVLink(NFVNode s, NFVNode d){
		setSrcNode(s);
		setDstNode(d);
		forwardProperty = new NFVLinkProperty("p1");
		backwardProperty = new NFVLinkProperty("p2");
	}

	public NFVNode getSrcNode() {
		return srcNode;
	}

	public void setSrcNode(NFVNode srcNode) {
		this.srcNode = srcNode;
	}

	public NFVNode getDstNode() {
		return dstNode;
	}

	public void setDstNode(NFVNode dstNode) {
		this.dstNode = dstNode;
	}
}
