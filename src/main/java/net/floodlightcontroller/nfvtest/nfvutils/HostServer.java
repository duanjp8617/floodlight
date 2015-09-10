package net.floodlightcontroller.nfvtest.nfvutils;

import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVNode;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVNode.CircularList;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVNode.SimpleSM;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.*;
import net.floodlightcontroller.nfvtest.nfvutils.MacAddressAllocator;
import net.floodlightcontroller.nfvtest.nfvutils.FakeDhcpAllocator;
import net.floodlightcontroller.nfvtest.nfvutils.IpAddressAllocator;
import net.floodlightcontroller.nfvtest.nfvutils.Pair;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HostServer {
	public class HostServerProperty{
		private final SimpleSM eth0InputState;
		private final SimpleSM eth0OutputState;
		public final CircularList<Float> eth0Input;
		public final CircularList<Float> eth0Output;
		
		public HostServerProperty(int listSize){
			this.eth0InputState = new SimpleSM(listSize);
			this.eth0OutputState = new SimpleSM(listSize);
			this.eth0Input = new CircularList<Float>(listSize, new Float(0));
			this.eth0Output = new CircularList<Float>(listSize, new Float(0));
		}
		
		public void updateServerProperty(Long eth0Input, Long eth0Output){
			float eth0InputBits = (eth0Input.floatValue())/(1024*1024)*8;
			float eth0OutputBits = (eth0Output.floatValue())/(1024*1024)*8;
			this.eth0Input.add(new Float(eth0InputBits));
			this.eth0Output.add(new Float(eth0OutputBits));
		}
		
		public int getNodeState(){
			if(eth0Input.getFilledUp()){
				this.eth0InputState.updateTransientState(checkStatus(this.eth0Input.getCircularList(),
																	 new Float(100.0),
																	 new Float(500.0)));
			}
			if(eth0Output.getFilledUp()){
				this.eth0OutputState.updateTransientState(checkStatus(this.eth0Output.getCircularList(),
																	 new Float(100.0),
																	 new Float(500.0)));
			}
			
			if( (this.eth0OutputState.getState() == NFVNode.OVERLOAD)||
				(this.eth0InputState.getState()==NFVNode.OVERLOAD)){
				return NFVNode.OVERLOAD;
			}
			else{
				return NFVNode.NORMAL;
			}
		}
		
		private <E extends Comparable<E>> int checkStatus(ArrayList<E> list, E lowerT, E upperT){
			int largerThanUpperT = 0;
			int smallerThanLowerT = 0;
			int inBetween = 0;
			
			for(E elem : list){
				if(elem.compareTo(upperT)>0){
					largerThanUpperT +=1;
				}
				else if(elem.compareTo(lowerT)<0){
					smallerThanLowerT +=1;
				}
				else{
					inBetween += 1;
				}
			}
			
			int returnVal = 0;
			
			if((largerThanUpperT == smallerThanLowerT)&&(largerThanUpperT == inBetween)){
				returnVal = NFVNode.NORMAL;
			}
			else if((largerThanUpperT >= smallerThanLowerT)&&(largerThanUpperT >= inBetween)){
				returnVal = NFVNode.OVERLOAD;
			}
			else if((inBetween>=largerThanUpperT)&&(inBetween>=smallerThanLowerT)){
				returnVal = NFVNode.NORMAL;
			}
			else if((smallerThanLowerT>=inBetween)&&(smallerThanLowerT>=largerThanUpperT)){
				returnVal = NFVNode.IDLE;
			}
			
			return returnVal;
		}
	}
	
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
		public final boolean isBufferNode;
		
		public VmInstance(HostServerConfig hConfig, ServiceChainConfig sConfig, ControllerConfig cConfig,
				   int stageIndex, String vmName,String managementMac, String managementIp, 
				   String operationMac, String operationIp, HostServer hostServer, boolean isBufferNode){
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
			this.isBufferNode = isBufferNode;
		}
		
		public VmInstance(HostServerConfig hConfig, ServiceChainConfig sConfig, ControllerConfig cConfig,
				   int stageIndex, String vmName, String managementMac, String managementIp, 
				   List<String> macList, List<String> dpidList, HostServer hostServer, boolean isBufferNode){
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
			this.isBufferNode = isBufferNode;
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
	public int tunnelPort;
	public int entryExitPort;
	public String entryMac;
	public String exitMac;
	
	private HostServerProperty serverProperty;
	private int serverState;
	private static Logger logger;
	
	public HostServer(ControllerConfig controllerConfig,
			   		  HostServerConfig hostServerConfig,
			   		  Map<String, ServiceChainConfig> serviceChainConfigMap,
			   		  MacAddressAllocator macAllocator,
			   		  IpAddressAllocator ipAllocator){
		this.controllerConfig = controllerConfig;
		this.hostServerConfig = hostServerConfig;
		this.serviceChainConfigMap = serviceChainConfigMap;
		
		this.macAllocator = macAllocator;
		this.serviceChainDpidMap = new HashMap<String, List<String>>();
		allocation = new HostServerAllocation(this.hostServerConfig);
		
		this.serviceChainMNetworkMap = new HashMap<String, FakeDhcpAllocator>();
		this.serviceChainONetworkMap = new HashMap<String, FakeDhcpAllocator>();
		
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
		
		this.tunnelPortMap = new HashMap<String, Integer>();
		this.tunnelPort = 10;
		this.entryExitPort=9;
		this.entryMac = "nil";
		this.exitMac = "nil";
		
		this.serverProperty = new HostServerProperty(10);
		this.serverState = NFVNode.IDLE;
		logger = LoggerFactory.getLogger(HostServer.class);
	}
	
	public VmInstance allocateVmInstance(String chainName, int stageIndex, boolean isBufferNode){
		if(allocation.allocate(serviceChainConfigMap.get(chainName), stageIndex)){
			ServiceChainConfig chainConfig = serviceChainConfigMap.get(chainName);
			VmInstance newVm;
			
			if(chainConfig.nVmInterface == 2){
				Pair<String, String> managementPair = 
						this.serviceChainMNetworkMap.get(chainName).allocateMacIp();
				Pair<String, String> operationPair = 
						this.serviceChainONetworkMap.get(chainName).allocateMacIp();
				
				String vmName = chainName+"-"+new Integer(stageIndex).toString()+
								"-"+managementPair.second+"-"+
								(isBufferNode?"b":"nb");
				
				newVm = new VmInstance(this.hostServerConfig, chainConfig, this.controllerConfig,
						stageIndex,vmName, managementPair.first, managementPair.second, 
						operationPair.first, operationPair.second, this, isBufferNode);
			}
			else{
				ArrayList<String> macAddrList = new ArrayList<String>();
				macAddrList.add(this.macAllocator.getMac());
				macAddrList.add(this.macAllocator.getMac());
				
				Pair<String, String> managementPair = 
						this.serviceChainMNetworkMap.get(chainName).allocateMacIp();
				
				String vmName = chainName+"-"+new Integer(stageIndex).toString()+
							    "-"+managementPair.second+"-"+
							    (isBufferNode?"b":"nb");
				
				newVm = new VmInstance(this.hostServerConfig, chainConfig, this.controllerConfig,
						stageIndex,vmName, managementPair.first, managementPair.second,
						macAddrList, this.serviceChainDpidMap.get(chainName), this, isBufferNode);
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
	
	public void updateNodeProperty(Long eth0Input, Long eth0Output){
		String stat = eth0Input.toString()+" "+eth0Output.toString();
		this.serverProperty.updateServerProperty(eth0Input, eth0Output);
		this.serverState  = this.serverProperty.getNodeState();
		
		if(this.serverState == NFVNode.OVERLOAD){
			String output = "Server-"+this.hostServerConfig.managementIp+" is OVERLOAD: "+stat;
			logger.info("{}", output);
		}
		if(this.serverState == NFVNode.NORMAL){
			String output = "Server-"+this.hostServerConfig.managementIp+" is NORMAL: "+stat;
			logger.info("{}", output);
		}
	}
	
	public int getState(){
		return this.serverState;
	}
}
