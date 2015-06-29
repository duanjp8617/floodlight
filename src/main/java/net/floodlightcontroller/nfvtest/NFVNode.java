package net.floodlightcontroller.nfvtest;
import java.util.ArrayList;
import java.util.List;



public abstract class NFVNode {
	private String hypervisorIpAddress;
	private List<String> nodeIpAddress;
	private List<String> nodeMacAddress;
	private List<Short> nodeVlanId;
	
	//A service chain may have multiple stages.
	private int stage;
	//Type of the network function
	private String type;
	//Ip address of the management interface
	private String nodeIndex;
	private boolean isInitialized;
	private int state;
	
	//node state: idel, normal, overload
	public static final int IDLE = 1;
	public static final int NORMAL = 2;
	public static final int OVERLOAD = 3;
	
	NFVNode(){
		setIsInitialized(false);
		nodeIpAddress = new ArrayList<String>();
		nodeMacAddress = new ArrayList<String>();
		nodeVlanId = new ArrayList<Short>();
	}
	
	NFVNode(NFVNode n){
		hypervisorIpAddress = new String(n.getHypervisorIpAddress());
		nodeIpAddress = new ArrayList<String>(n.getNodeIpAddress());
		nodeMacAddress = new ArrayList<String>(n.getNodeMacAddress());
		nodeVlanId = new ArrayList<Short>(n.getNodeVlanId());
		stage = n.getStage();
		type = new String(n.getType());
		nodeIndex = new String(n.getNodeIndex());
		isInitialized = n.getIsInitialized();
		state = n.getState();
	}
	
	public void setState(int s){
		state = s;
	}
	
	public int getState(){
		return state;
	}
	
	
	public void setHypervisorIpAddress(String ip){
		String tmp = new String(ip);
		hypervisorIpAddress = tmp;
	}
	
	public String getHypervisorIpAddress(){
		return hypervisorIpAddress;
	}
	
	public void setStage(int stageNum){
		stage = stageNum;
	}
	
	public int getStage(){
		return stage;
	}
	
	public void setType(String t){
		String tmp = new String(t);
		type = tmp;
	}
	
	public String getType(){
		return type;
	}
	
	public void setNodeIndex(String index){
		String tmp = new String(index);
		nodeIndex = tmp;
	}
	
	public String getNodeIndex(){
		return nodeIndex;
	}
	
	public void setIsInitialized(boolean b){
		isInitialized = b;
	}
	
	public boolean getIsInitialized(){
		return isInitialized;
	}
	
	public void addNodeIpAddress(String ip){
		String tmp = new String(ip);
		nodeIpAddress.add(tmp);
	}
	
	public void addNodeMacAddress(String m){
		String tmp = new String(m);
		nodeMacAddress.add(tmp);
	}
	
	public void addVlanId(short vlanid){
		nodeVlanId.add(vlanid);
	}
	
	public String getNodeIpAddress(int index){
		return nodeIpAddress.get(index);
	}
	
	public List<String> getNodeIpAddress(){
		return nodeIpAddress;
	}
	
	public String getNodeMacAddress(int index){
		return nodeMacAddress.get(index);
	}
	
	public List<String> getNodeMacAddress(){
		return nodeMacAddress;
	}
	
	public short getNodeVlanId(int index){
		return nodeVlanId.get(index);
	}
	
	public List<Short> getNodeVlanId(){
		return nodeVlanId;
	}
	
}
