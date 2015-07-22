package net.floodlightcontroller.nfvtest.nfvutils;

import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.*;
import net.floodlightcontroller.nfvtest.nfvutils.MacAddressAllocator;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

public class HostServer {
	
	public class VmInstance {
		public final ControllerConfig controllerConfig;
		public final HostServerConfig hostServerConfig;
		public final ServiceChainConfig serviceChainConfig;
		public final String vmName;
		public final int stageIndex;
		public final List<String> macList;
		public final Map<String, String> macBridgeMap;
		
		public VmInstance(HostServerConfig hConfig, ServiceChainConfig sConfig, ControllerConfig cConfig,
				   int stageIndex, String vmName, List<String> macList){
			this.stageIndex = stageIndex;
			this.vmName = vmName;
			this.hostServerConfig = hConfig;
			this.serviceChainConfig = sConfig;
			this.macList = macList;
			this.macBridgeMap = new HashMap<String, String>();
			this.controllerConfig = cConfig;
			
			if(this.macList.size()==2){
				macBridgeMap.put(this.macList.get(0), this.serviceChainConfig.bridges.get(stageIndex));
				macBridgeMap.put(this.macList.get(1), 
				        this.serviceChainConfig.bridges.get(this.serviceChainConfig.bridges.size()-1));
			}
			else if(this.macList.size()==3){
				macBridgeMap.put(this.macList.get(0), this.serviceChainConfig.bridges.get(stageIndex));
				macBridgeMap.put(this.macList.get(1), this.serviceChainConfig.bridges.get(stageIndex+1));
				macBridgeMap.put(this.macList.get(2), 
				        this.serviceChainConfig.bridges.get(this.serviceChainConfig.bridges.size()-1));
			}
			else{
			}
		}
		
		public StageVmInfo getStageVmInfo(){
			return serviceChainConfig.getStageVmInfo(this.stageIndex);
		}
	}
	
	public class HostServerAllocation{
		private int cpu;
		private int mem;
		private int storage;
		final HostServerConfig hostServerConfig;
		
		HostServerAllocation(HostServerConfig hostServerConfig){
			this.cpu = 0;
			this.mem = 0;
			this.storage = 0;
			this.hostServerConfig = hostServerConfig;
		}
		
		public boolean allocate(ServiceChainConfig chainConfig, int stageIndex){
			StageVmInfo vmInfo = chainConfig.getStageVmInfo(stageIndex);
			if( ((vmInfo.cpu+this.cpu)<=this.hostServerConfig.cpuCapacity) && 
				((vmInfo.mem+this.mem)<=this.hostServerConfig.memoryCapacity) &&
				((vmInfo.storage+this.storage)<=this.hostServerConfig.storageCapacity) ){
				this.cpu += vmInfo.cpu;
				this.mem += vmInfo.mem;
				this.storage += vmInfo.storage;
				return true;
			}
			else{
				return false;
			}
		}
		
		public boolean deallocate(ServiceChainConfig chainConfig, int stageIndex){
			StageVmInfo vmInfo = chainConfig.getStageVmInfo(stageIndex);
			if( ((this.cpu-vmInfo.cpu)>=0) && 
				((this.mem-vmInfo.mem)>=0) &&
				((this.storage-vmInfo.storage)>=0) ){
				this.cpu -= vmInfo.cpu;
				this.mem -= vmInfo.mem;
				this.storage -= vmInfo.storage;
				return true;
			}
			else{
				return false;
			}
		}
	}
	
	public final ControllerConfig controllerConfig;
	public final HostServerConfig hostServerConfig;
	public final Map<String, ServiceChainConfig> serviceChainConfigMap;
	public final MacAddressAllocator macAllocator;
	private HostServerAllocation allocation;
	
	public HostServer(ControllerConfig controllerConfig,
			   		  HostServerConfig hostServerConfig,
			   		  Map<String, ServiceChainConfig> serviceChainConfigMap,
			   		  MacAddressAllocator macAllocator){
		this.controllerConfig = controllerConfig;
		this.hostServerConfig = hostServerConfig;
		this.serviceChainConfigMap = serviceChainConfigMap;
		this.macAllocator = macAllocator;
		allocation = new HostServerAllocation(this.hostServerConfig);
	}
	
	public VmInstance allocateVmInstance(String chainName, int stageIndex){
		if(allocation.allocate(serviceChainConfigMap.get(chainName), stageIndex)){
			ServiceChainConfig chainConfig = serviceChainConfigMap.get(chainName);

			ArrayList<String> macAddrList = new ArrayList<String>();
			for(int i=0; i<chainConfig.nVmInterface; i++){
				macAddrList.add(this.macAllocator.getMac());
			}

			String lastMacAddr = new String(macAddrList.get(macAddrList.size()-1));
			String newStr = lastMacAddr.replace(':', '-');
			String vmName = chainName+"-"+newStr;
			VmInstance newVm = new VmInstance(this.hostServerConfig, chainConfig, this.controllerConfig, stageIndex,
			      vmName,macAddrList);	
			return newVm;
		}
		else{
			return null;
		}
	}
	
	public boolean deallocateVmInstance(VmInstance vm){
		if(this.allocation.deallocate(vm.serviceChainConfig, vm.stageIndex)){
			return true;
		}
		else{
			return false;
		}
	}
}
