package net.floodlightcontroller.nfvtest.nfvutils;

import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.*;
import net.floodlightcontroller.nfvtest.nfvutils.HostAgent;
import net.floodlightcontroller.nfvtest.nfvutils.MacAddressAllocator;

import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;

public class HostServer {
	
	public class VmInstance {
		final HostServerConfig hostServerConfig;
		final ServiceChainConfig serviceChainConfig;
		final String vmName;
		final int stageIndex;
		final List<String> macList;
		final Map<String, String> macBridgeMap;
		
		VmInstance(HostServerConfig hConfig, ServiceChainConfig sConfig, int stageIndex, String vmName,
				   List<String> macList){
			this.stageIndex = stageIndex;
			this.vmName = vmName;
			this.hostServerConfig = hConfig;
			this.serviceChainConfig = sConfig;
			this.macList = macList;
			this.macBridgeMap = new HashMap<String, String>();
			
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
		
		StageVmInfo getStageVmInfo(){
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
		
		public synchronized boolean allocate(ServiceChainConfig chainConfig, int stageIndex){
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
		
		public synchronized boolean deallocate(ServiceChainConfig chainConfig, int stageIndex){
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
	
	final ControllerConfig controllerConfig;
	final HostServerConfig hostServerConfig;
	final Map<String, ServiceChainConfig> serviceChainConfigMap;
	final MacAddressAllocator macAllocator;
	private HostServerAllocation allocation;
	
	HostServer(ControllerConfig controllerConfig,
			   HostServerConfig hostServerConfig,
			   Map<String, ServiceChainConfig> serviceChainConfigMap,
			   MacAddressAllocator macAllocator){
		this.controllerConfig = controllerConfig;
		this.hostServerConfig = hostServerConfig;
		this.serviceChainConfigMap = serviceChainConfigMap;
		this.macAllocator = macAllocator;
		allocation = new HostServerAllocation(this.hostServerConfig);
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
	
	private String constructLocalXmlFile(VmInstance vmInstance){
		Document doc;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		
		String vmName = vmInstance.vmName;
		StageVmInfo vmInfo = vmInstance.getStageVmInfo();
		String xmlTemplateFile =controllerConfig.xmlDir+"/"+controllerConfig.xmlTemplateName;
		String destImgFile = vmInstance.hostServerConfig.imgDir+"/"+vmName;
		String localXmlFile = controllerConfig.xmlDir+"/"+vmName;
		
		try{
			DocumentBuilder db = dbf.newDocumentBuilder();
			doc = db.parse(new File(xmlTemplateFile));
			doc.getDocumentElement().normalize();
			
			Node devices = doc.getElementsByTagName("devices").item(0);
			
			Node name = doc.getElementsByTagName("name").item(0);
			name.setTextContent(vmName);
			
			Node memory = doc.getElementsByTagName("memory").item(0);
			memory.setTextContent(new Integer(vmInfo.mem).toString());
			
			Node currentMemory = doc.getElementsByTagName("currentMemory").item(0);
			currentMemory.setTextContent(new Integer(vmInfo.mem).toString());
			
			Node vcpu = doc.getElementsByTagName("vcpu").item(0);
			vcpu.setTextContent(new Integer(vmInfo.cpu).toString());
			
			NodeList diskList = doc.getElementsByTagName("disk");
			for(int i=0; i<diskList.getLength(); i++){
				Node disk = diskList.item(i);
				NamedNodeMap attr = disk.getAttributes();
				Node type = attr.getNamedItem("type");
				
				if(type.getTextContent().equals("file")){
					Element eDisk = (Element)disk;
					Node source = eDisk.getElementsByTagName("source").item(0);
					Element eSource = (Element)source;
					eSource.setAttribute("file", destImgFile);
				}
			}
	
			for(String macAddr : vmInstance.macBridgeMap.keySet()){
				String bridge = vmInstance.macBridgeMap.get(macAddr);
				
				Element eInterface = doc.createElement("interface");
				eInterface.setAttribute("type", "bridge");
			
				Element eMac = doc.createElement("mac");
				eMac.setAttribute("address", macAddr);
				eInterface.appendChild(eMac);
			
				Element eSource = doc.createElement("source");
				eSource.setAttribute("bridge", bridge);
				eInterface.appendChild(eSource);
			
				Element eVirtualPort = doc.createElement("virtualport");
				eVirtualPort.setAttribute("type", "openvswitch");
				eInterface.appendChild(eVirtualPort);
			
				Element eModel = doc.createElement("model");
				eModel.setAttribute("type", "virtio");
				eInterface.appendChild(eModel);
			
				devices.appendChild(eInterface);
			}
			
			doc.getDocumentElement().normalize();
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			
			File constructedFile = new File(localXmlFile);
			StreamResult result = new StreamResult(constructedFile);
			DOMSource source = new DOMSource(doc);
			transformer.transform(source, result);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		return localXmlFile;
	}
	
	public void createVm(String chainName, int stageIndex){
		if(allocation.allocate(serviceChainConfigMap.get(chainName), stageIndex)){
			//This host has enough resource to allocate a corresponding vm.
			ServiceChainConfig chainConfig = serviceChainConfigMap.get(chainName);
			
			ArrayList<String> macAddrList = new ArrayList<String>();
			for(int i=0; i<chainConfig.nVmInterface; i++){
				macAddrList.add(this.macAllocator.getMac());
			}
			
			String lastMacAddr = new String(macAddrList.get(macAddrList.size()-1));
			String vmName = chainName+"-"+lastMacAddr;
			VmInstance newVm = new VmInstance(this.hostServerConfig, chainConfig, stageIndex,
										      vmName,macAddrList);
			
			String localXmlFile = this.constructLocalXmlFile(newVm);
			String remoteXmlFile = this.controllerConfig.xmlDir+"/"+vmName;
			String remoteImgFile = this.controllerConfig.imgDir+"/"+vmName;
			String remoteBaseImgFile = this.hostServerConfig.imgDir+"/"+
			                           chainConfig.getImgNameForStage(stageIndex);
			
			HostAgent agent = new HostAgent(this.hostServerConfig);
			try{
				agent.uploadFile(localXmlFile, remoteXmlFile);
				agent.copyFile(remoteBaseImgFile, remoteImgFile);
				agent.createVMFromXml(remoteXmlFile);
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
	}
}
