package net.floodlightcontroller.nfvtest.nfvcorestructure;
import java.util.ArrayList;
import java.util.List;

import net.floodlightcontroller.nfvtest.nfvutils.HostServer.VmInstance;



public class NFVNode {
	//immutable field.
	private final String hypervisorIpAddress;
	private final List<String> nodeIpAddress;
	private final List<String> nodeMacAddress;
	private final List<Short> nodeVlanId;
	
	private final int stage;
	private final String type;
	private final String nodeIndex;
	private final int state;
	
	private NFVNodeProperty property;
	
	//node state: idel, normal, overload
	public static final int IDLE = 1;
	public static final int NORMAL = 2;
	public static final int OVERLOAD = 3;
	
	public static class NFVNodeProperty{
		private String fakeProperty;
		NFVNodeProperty(String fakeProperty){
			this.fakeProperty = new String(fakeProperty);
		}
		public String getFakeProperty(){
			return fakeProperty;
		}
		public void setFakeProperty(String newProperty){
			this.fakeProperty = new String(newProperty);
		}
	}
	
	NFVNode(String hypervisorIpAddress, List<String> nodeIpAddress, List<String> nodeMacAddress,
			List<Short> nodeVlanId,     int stage,                  String type,
			String nodeIndex,           int state){
		this.hypervisorIpAddress = new String(hypervisorIpAddress);
		this.stage = stage;
		this.type = new String(type);
		this.nodeIndex = new String(nodeIndex);
		this.state = state;
		
		this.nodeIpAddress = new ArrayList<String>(nodeIpAddress);
		this.nodeMacAddress = new ArrayList<String>(nodeMacAddress);
		this.nodeVlanId = new ArrayList<Short>(nodeVlanId);
		this.property = new NFVNodeProperty("non zero");
	}
	
	NFVNode(NFVNode n){
		hypervisorIpAddress = new String(n.getHypervisorIpAddress());
		stage = n.getStage();
		type = new String(n.getType());
		nodeIndex = new String(n.getNodeIndex());
		state = n.getState();
		
		nodeIpAddress = new ArrayList<String>(n.getNodeIpAddress());
		nodeMacAddress = new ArrayList<String>(n.getNodeMacAddress());
		nodeVlanId = new ArrayList<Short>(n.getNodeVlanId());
		this.property = new NFVNodeProperty("non zero");
	}
	
	
	public int getState(){
		return state;
	}
	
	public String getHypervisorIpAddress(){
		return hypervisorIpAddress;
	}
	
	public int getStage(){
		return stage;
	}

	public String getType(){
		return type;
	}
	
	public String getNodeIndex(){
		return nodeIndex;
	}
	
	public List<String> getNodeIpAddress(){
		return nodeIpAddress;
	}
	
	public List<String> getNodeMacAddress(){
		return nodeMacAddress;
	}
	
	public List<Short> getNodeVlanId(){
		return nodeVlanId;
	}

	public NFVNodeProperty getProperty() {
		return property;
	}
	
}
