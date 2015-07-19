package net.floodlightcontroller.nfvtest.nfvutils;

import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.*;
import net.floodlightcontroller.nfvtest.nfvutils.HostAgent;
import java.util.Map;

public class HostServer {
	final ControllerConfig controllerConfig;
	final HostServerConfig hostServerConfig;
	final Map<String, ServiceChainConfig> serviceChainConfigMap;
	
	HostServer(ControllerConfig controllerConfig,
			   HostServerConfig hostServerConfig,
			   Map<String, ServiceChainConfig> serviceChainConfigMap){
		this.controllerConfig = controllerConfig;
		this.hostServerConfig = hostServerConfig;
		this.serviceChainConfigMap = serviceChainConfigMap;
	}
	
	public void initialize(){
		HostAgent agent = new HostAgent(this.hostServerConfig);
		try{
			agent.createDir(hostServerConfig.homeDir);
			agent.createDir(hostServerConfig.xmlDir);
			agent.createDir(hostServerConfig.imgDir);
			
			for(String chainName : serviceChainConfigMap.keySet()){
				ServiceChainConfig chainConfig = serviceChainConfigMap.get(chainName);
				for(int i=0; i<chainConfig.bridges.size(); i++){
					agent.createBridge(chainConfig.bridges.get(i));
				}
				for(int i=0; i<chainConfig.stages.size(); i++){
					String imgPath = controllerConfig.imgDir+"/"+chainConfig.getImgNameForStage(i);
					String remotePath = hostServerConfig.imgDir+"/"+chainConfig.getImgNameForStage(i);
					agent.uploadFile(imgPath, remotePath);
				}
			}
		}
		catch (Exception e){
		}
	}
	
	
}
