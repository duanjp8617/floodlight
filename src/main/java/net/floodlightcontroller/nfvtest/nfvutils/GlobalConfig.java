package net.floodlightcontroller.nfvtest.nfvutils;

import java.util.List;
import java.util.ArrayList;

public class GlobalConfig {
	
	static public class ControllerConfig{
		public final String managementIp;
		public final String homeDir;
		public final String imgDir;
		public final String xmlDir;
		public final String xmlTemplateName;
		public final String networkTemplateName;
		
		public ControllerConfig(String managementIp, String homeDir, String xmlTemplateName,
								String networkTemplateName){
			this.managementIp = managementIp;
			this.homeDir = homeDir;
			this.imgDir = homeDir+"/img";
			this.xmlDir = homeDir+"/xml";
			this.xmlTemplateName = xmlTemplateName;
			this.networkTemplateName = networkTemplateName;
		}
	}
	
	static public class HostServerConfig{
		public final String managementIp;
		public final String internalIp;
		public final String publicIp;
		
		public final int  cpuCapacity;     //in # of cores
		public final int memoryCapacity; //in Mbytes
		public final int storageCapacity; //in Mbytes
		public final int interfaceSpeed; //in Gbps
		
		public final String userName;
		public final String passWord;
		
		public final String homeDir;
		public final String imgDir;
		public final String xmlDir;
		
		public HostServerConfig(String managementIp, String internalIp, String publicIp,
		   		   	     		int cpuCapacity, int memoryCapacity, int storageCapacity,
		   		   	     		int interfaceSpeed, String userName, String passWord, String homeDir){
			this.managementIp = managementIp;
			this.internalIp = internalIp;
			this.publicIp = publicIp;
			this.cpuCapacity = cpuCapacity;
			this.memoryCapacity = memoryCapacity;
			this.storageCapacity = storageCapacity;
			this.interfaceSpeed = interfaceSpeed;
			this.userName = userName;
			this.passWord = passWord;
			this.homeDir = homeDir;
			this.imgDir = homeDir+"/img";
			this.xmlDir = homeDir+"/xml";
		}
		
		public boolean equals(HostServerConfig anotherHost){
			if(this.managementIp == anotherHost.managementIp){
				return true;
			}
			else{
				return false;
			}
		}
	}
	
	static public class StageVmInfo{
		public final int cpu;
		public final int mem;     //in MB
		public final int storage; //in MB
		public final String imageName;
		
		public StageVmInfo(int cpu, int mem, int storage, String imageName){
			this.cpu = cpu;
			this.mem = mem;
			this.storage = storage;
			this.imageName = imageName;
		}
	}
	
	static public class ServiceChainConfig{
		public final String name;
		public final int nVmInterface;
		public final List<StageVmInfo> stages;
		public final List<String> bridges;
		
		private String managementNetwork;
		private String operationNetwork;
		
		//may need some additional informations.
		public ServiceChainConfig(String name, int nVmInterface, List<StageVmInfo> stages){
			this.name = name;
			this.nVmInterface = nVmInterface;
			this.stages = stages;
			this.bridges = new ArrayList<String>();
			this.managementNetwork = this.name+"-"+"m";

			if(nVmInterface == 2){
				this.operationNetwork = this.name+"-"+"o";
			}
			if(nVmInterface == 3){
				for(int i=0; i<stages.size()+1; i++){
					bridges.add(this.name+"-"+"br"+(new Integer(i).toString()));
				}
				this.operationNetwork = "nil";
			}
		}
		
		public String getOperationNetwork(){
			return this.operationNetwork;
		}
		
		public String getManagementNetwork(){
			return this.managementNetwork;
		}
		
		public StageVmInfo getStageVmInfo(int stageIndex){
			return stages.get(stageIndex);
		}
		
		public String getImgNameForStage(int stageIndex){
			return stages.get(stageIndex).imageName;
		}
	}
	
}
