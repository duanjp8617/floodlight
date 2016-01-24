package net.floodlightcontroller.nfvtest.nfvutils;

import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.*;
import net.floodlightcontroller.nfvtest.nfvutils.MacAddressAllocator;
import net.floodlightcontroller.nfvtest.nfvutils.FakeDhcpAllocator;
import net.floodlightcontroller.nfvtest.nfvutils.IpAddressAllocator;
import net.floodlightcontroller.nfvtest.nfvutils.Pair;

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
		public final List<String> bridgeDpidList;
		private int[] port;
		
		public final String managementMac;
		public final String managementIp;
		public final String operationMac;
		public final String operationIp;
		
		public final HostServer hostServer;
		
		public VmInstance(HostServerConfig hConfig, ServiceChainConfig sConfig, ControllerConfig cConfig,
				   int stageIndex, String vmName,String managementMac, String managementIp, 
				   String operationMac, String operationIp, HostServer hostServer){
			this.stageIndex = stageIndex;
			this.vmName = vmName;
			
			this.controllerConfig = cConfig;
			this.hostServerConfig = hConfig;
			this.serviceChainConfig = sConfig;
			
			this.macList = new ArrayList<String>();
			this.macBridgeMap = new HashMap<String, String>();
			this.bridgeDpidList = new ArrayList<String>();
			
			this.managementMac = managementMac;
			this.managementIp = managementIp;
			this.operationMac = operationMac;
			this.operationIp = operationIp;
			
			this.hostServer = hostServer;
		}
		
		public VmInstance(HostServerConfig hConfig, ServiceChainConfig sConfig, ControllerConfig cConfig,
				   int stageIndex, String vmName, String managementMac, String managementIp, 
				   List<String> macList, List<String> dpidList, HostServer hostServer){
			this.stageIndex = stageIndex;
			this.vmName = vmName;
			
			this.controllerConfig = cConfig;
			this.hostServerConfig = hConfig;
			this.serviceChainConfig = sConfig;
			
			this.macList = macList;
			this.macBridgeMap = new HashMap<String, String>();
			this.bridgeDpidList = new ArrayList<String>();
			
			this.managementMac = managementMac;
			this.managementIp = managementIp;
			this.operationMac = "nil";
			this.operationIp = "nil";
			
			this.macBridgeMap.put(this.macList.get(0), 
					              this.serviceChainConfig.bridges.get(this.stageIndex));
			this.macBridgeMap.put(this.macList.get(1), 
		              			  this.serviceChainConfig.bridges.get(this.stageIndex+1));
			this.bridgeDpidList.add(dpidList.get(this.stageIndex));
			this.bridgeDpidList.add(dpidList.get(this.stageIndex+1));
			
			this.hostServer = hostServer;
		}
		
		public StageVmInfo getStageVmInfo(){
			return serviceChainConfig.getStageVmInfo(this.stageIndex);
		}
		
		public void setPort(int[] port){
			this.port = port;
		}
		
		public synchronized int getPort(int index){
			if(index>=this.macList.size()){
				return -10;
			}
			else{
				return this.port[index];
			}
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
	public final Map<String, List<String>> serviceChainDpidMap;
	
	public final Map<String, FakeDhcpAllocator> serviceChainMNetworkMap;
	public final Map<String, FakeDhcpAllocator> serviceChainONetworkMap;
	
	private HostServerAllocation allocation;
	
	public final Map<String, Integer> tunnelPortMap;
	public final Map<Integer, String> portTunnelMap;
	public int tunnelPort;

	public String gatewayMac;
	public String gatewayIp;
	public int gatewayPort;
	
	public String exitMac;
	public String exitIp;
	public int exitPort;
	
	public String entryIp;
	
	public int patchPort;
	public String frontPortName;
	public String rearPortName;
	
	//For inter-datacenter tunnels
	public final Map<Integer, Integer> dcIndexPortMap;
	public final Map<Integer, Integer> portDcIndexMap;
	
	public final HashMap<Integer, ArrayList<Integer>> dcIndexPatchPortListMap;
	
	public final String statBridgeDpid;
	public final int statInPort;
	public final int statOutPort;
	
	public HostServer(ControllerConfig controllerConfig,
			   		  HostServerConfig hostServerConfig,
			   		  Map<String, ServiceChainConfig> serviceChainConfigMap,
			   		  MacAddressAllocator macAllocator,
			   		  IpAddressAllocator ipAllocator,
			   		  String entryIp,
			   		  String gatewayIp,
			   		  String exitIp){
		this.controllerConfig = controllerConfig;
		this.hostServerConfig = hostServerConfig;
		this.serviceChainConfigMap = serviceChainConfigMap;
		
		this.macAllocator = macAllocator;
		this.serviceChainDpidMap = new HashMap<String, List<String>>();
		allocation = new HostServerAllocation(this.hostServerConfig);
		
		this.serviceChainMNetworkMap = new HashMap<String, FakeDhcpAllocator>();
		this.serviceChainONetworkMap = new HashMap<String, FakeDhcpAllocator>();
		
		this.dcIndexPortMap = new HashMap<Integer, Integer>();
		this.portDcIndexMap = new HashMap<Integer, Integer>();
		
		this.dcIndexPatchPortListMap = new HashMap<Integer, ArrayList<Integer>>();
		
		for(String chainName : this.serviceChainConfigMap.keySet()){
			
			ServiceChainConfig chainConfig = this.serviceChainConfigMap.get(chainName);
			
			List<String> dpidList = new ArrayList<String>();
			for(int i=0; i<chainConfig.bridges.size(); i++){
				String dpid = this.macAllocator.getMac();
				dpidList.add(dpid);
			}
			serviceChainDpidMap.put(chainName, dpidList);
			
			FakeDhcpAllocator mDhcpAllocator = new FakeDhcpAllocator(this.macAllocator,
												ipAllocator.allocateIp()+2, 32);
			this.serviceChainMNetworkMap.put(chainName, mDhcpAllocator);
			
			if(chainConfig.nVmInterface == 2){
				FakeDhcpAllocator oDhcpAllocator = new FakeDhcpAllocator(this.macAllocator,
														ipAllocator.allocateIp()+2, 32);
				this.serviceChainONetworkMap.put(chainName, oDhcpAllocator);
			}
			else{
				this.serviceChainONetworkMap.put(chainName, null);
			}
		}
		
		this.statBridgeDpid = this.macAllocator.getMac();
		this.statInPort = 6;
		this.statOutPort = 5;
		
		this.tunnelPortMap = new HashMap<String, Integer>();
		this.portTunnelMap = new HashMap<Integer, String>();
		this.tunnelPort = 10;
		this.gatewayPort = 9;
		this.exitPort = 8;
		this.patchPort = 7;
		this.gatewayMac = "nil";
		this.exitMac = "nil";
		this.gatewayIp = gatewayIp;
		this.exitIp = exitIp;
		this.entryIp = entryIp;
		
		this.frontPortName = "front";
		this.rearPortName = "rear";
	}
	
	public VmInstance allocateVmInstance(String chainName, int stageIndex){
		if(allocation.allocate(serviceChainConfigMap.get(chainName), stageIndex)){
			ServiceChainConfig chainConfig = serviceChainConfigMap.get(chainName);
			VmInstance newVm;
			
			if(chainConfig.nVmInterface == 2){
				Pair<String, String> managementPair = 
						this.serviceChainMNetworkMap.get(chainName).allocateMacIp();
				Pair<String, String> operationPair = 
						this.serviceChainONetworkMap.get(chainName).allocateMacIp();
				
				String vmName = chainName+"-"+new Integer(stageIndex).toString()+
								"-"+managementPair.second;
				
				newVm = new VmInstance(this.hostServerConfig, chainConfig, this.controllerConfig,
						stageIndex,vmName, managementPair.first, managementPair.second, 
						operationPair.first, operationPair.second, this);
			}
			else{
				ArrayList<String> macAddrList = new ArrayList<String>();
				macAddrList.add(this.macAllocator.getMac());
				macAddrList.add(this.macAllocator.getMac());
				
				Pair<String, String> managementPair = 
						this.serviceChainMNetworkMap.get(chainName).allocateMacIp();
				
				String vmName = chainName+"-"+new Integer(stageIndex).toString()+
								"-"+managementPair.second;
				
				newVm = new VmInstance(this.hostServerConfig, chainConfig, this.controllerConfig,
						stageIndex,vmName, managementPair.first, managementPair.second,
						macAddrList, this.serviceChainDpidMap.get(chainName), this);
			}
			
			return newVm;
		}
		else{
			return null;
		}
	}
	
	public boolean deallocateVmInstance(VmInstance vm){
		if(this.allocation.deallocate(vm.serviceChainConfig, vm.stageIndex)){
			Pair<String, String> managementPair = new Pair<String, String>(vm.managementMac, vm.managementIp);
			this.serviceChainMNetworkMap.get(vm.serviceChainConfig.name).deallocateMacIp(managementPair);
			
			if(vm.serviceChainConfig.nVmInterface ==2){
				Pair<String, String> operationPair = new Pair<String, String>(vm.operationMac, vm.operationIp);
				this.serviceChainONetworkMap.get(vm.serviceChainConfig.name).deallocateMacIp(operationPair);
			}
			return true;
		}
		else{
			return false;
		}
	}
}
