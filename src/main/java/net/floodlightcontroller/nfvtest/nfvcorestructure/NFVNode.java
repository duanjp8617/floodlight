package net.floodlightcontroller.nfvtest.nfvcorestructure;

import net.floodlightcontroller.nfvtest.nfvutils.HostServer.VmInstance;



public class NFVNode {
	//immutable field.
	public final VmInstance vmInstance;
	
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
	
	public NFVNode(VmInstance vmInstance){
		this.vmInstance = vmInstance;
	}
	
	public String getChainName(){
		return this.vmInstance.serviceChainConfig.name;
	}
	
	public int getNumInterfaces(){
		return this.vmInstance.serviceChainConfig.nVmInterface;
	}
	
	public String getMacAddress(int whichMac){
		if(whichMac>=this.vmInstance.serviceChainConfig.nVmInterface){
			return "no-such-mac";
		}
		else{
			return this.vmInstance.macList.get(whichMac);
		}
	}
	
	public String getBridgeDpid(int whichMac){
		if(whichMac>=this.vmInstance.serviceChainConfig.nVmInterface){
			return "no-such-bridge";
		}
		else{
			return this.vmInstance.bridgeDpidList.get(whichMac);
		}
	}
	
	public int getPort(int whichMac){
		if(whichMac>=this.vmInstance.serviceChainConfig.nVmInterface){
			return -10;
		}
		else{
			return this.vmInstance.getPort(whichMac);
		}
	}
	
	public String getNodeIndex(){
		return this.vmInstance.macList.get(this.vmInstance.macList.size()-1);
	}
	
	public String getManagementMac(){
		return this.vmInstance.managementMac;
	}
	
}
