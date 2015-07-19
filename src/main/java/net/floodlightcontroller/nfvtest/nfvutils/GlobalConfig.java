package net.floodlightcontroller.nfvtest.nfvutils;

import java.util.List;
import java.util.ArrayList;

public class GlobalConfig {
	
	public class ControllerConfig{
		public final String managementIp;
		public final String homeDir;
		public final String imgDir;
		public final String xmlDir;
		public final String xmlTemplateName;
		
		ControllerConfig(String managementIp, String homeDir, String xmlTemplateName){
			this.managementIp = managementIp;
			this.homeDir = homeDir;
			this.imgDir = homeDir+"/img";
			this.xmlDir = homeDir+"/xml";
			this.xmlTemplateName = xmlTemplateName;
		}
	}
	
	public class HostServerConfig{
		final String managementIp;
		final String internalIp;
		final String publicIp;
		
		final int  cpuCapacity;     //in # of cores
		final long memoryCapacity; //in Mbytes
		final long storageCapacity; //in Mbytes
		final long interfaceSpeed; //in Gbps
		
		final String userName;
		final String passWord;
		
		final String homeDir;
		final String imgDir;
		final String xmlDir;
		
		HostServerConfig(String managementIp, String internalIp, String publicIp,
		   		   	     int cpuCapacity, long memoryCapacity, long storageCapacity,
		                 long interfaceSpeed, String userName, String passWord, String homeDir){
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
	}
	
	public class StageVmInfo{
		final int cpu;
		final int mem;     //in kB
		final int storage; //in MB
		final String imageName;
		
		StageVmInfo(int cpu, int mem, int storage, String imageName){
			this.cpu = cpu;
			this.mem = mem;
			this.storage = storage;
			this.imageName = imageName;
		}
	}
	
	public class ServiceChainConfig{
		final String name;
		final int nVmInterface;
		final List<StageVmInfo> stages;
		final List<String> bridges;
		
		//may need some additional informations.
		ServiceChainConfig(String name, int nVmInterface, List<StageVmInfo> stages){
			this.name = name;
			this.nVmInterface = nVmInterface;
			this.stages = stages;
			this.bridges = new ArrayList<String>();
			
			for(int i=0; i<stages.size()+(this.nVmInterface-1); i++){
				bridges.add(this.name+"-"+"br"+(new Integer(i).toString()));
			}
		}
		
		StageVmInfo getStageVmInfo(int stageIndex){
			return stages.get(stageIndex);
		}
		
		String getImgNameForStage(int stageIndex){
			return stages.get(stageIndex).imageName;
		}
	}
	
}
