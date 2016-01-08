package net.floodlightcontroller.nfvtest.nfvslaveservice;

import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.ControllerConfig;
import net.floodlightcontroller.nfvtest.nfvutils.HostAgent;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.ServiceChainConfig;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.StageVmInfo;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer.VmInstance;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;
import net.floodlightcontroller.nfvtest.nfvutils.FakeDhcpAllocator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VmWorker extends MessageProcessor{
	
	private final Logger logger =  LoggerFactory.getLogger(VmWorker.class);
	
	public VmWorker(String id){
		this.id = id;
		this.queue = new LinkedBlockingQueue<Message>();
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		System.out.println("VmWorker is started");
		LinkedBlockingQueue<Message> q = (LinkedBlockingQueue<Message>)this.queue;
		while(true){
			try{
				Message m = q.take();
				if(m instanceof KillSelfRequest){
					break;
				}
				else{
					onReceive(m);
				}
			}
			catch (Exception e){
				e.printStackTrace();
			}
			
		}
		
	}

	@Override
	protected void onReceive(Message m) {
		// TODO Auto-generated method stub
		if(m instanceof CreateVmRequest){
			CreateVmRequest request = (CreateVmRequest)m;
			createVm(request);
		}
		else if(m instanceof HostInitializationRequest){
			HostInitializationRequest request = (HostInitializationRequest)m;
			initialize(request);
		}
		else if(m instanceof DestroyVmRequest){
			DestroyVmRequest request = (DestroyVmRequest)m;
			destroyVm(request);
		}
	}
	
	private void initialize(HostInitializationRequest request){
		HostServer hostServer = request.getHostServer();
		HostAgent agent = new HostAgent(hostServer.hostServerConfig);
		try{
			//agent will connect to the host server
			//then create dirs on host server if 
			//dirs do not exist. If dirs exist, 
			//we remove previous uploaded xml files
			agent.connect();
			agent.createDir(hostServer.hostServerConfig.homeDir);
			agent.createDir(hostServer.hostServerConfig.xmlDir);
			agent.createDir(hostServer.hostServerConfig.imgDir);
			agent.removeFilesFromDir(hostServer.hostServerConfig.xmlDir);
			
			//The following loop initialize the runtime environment in host server.
			//All used service chains will be initialized in turns.
			ArrayList<String> baseImgList = new ArrayList<String>();
			for(String chainName : hostServer.serviceChainConfigMap.keySet()){
				ServiceChainConfig chainConfig = hostServer.serviceChainConfigMap.get(chainName);
				
				//create ovs bridges on host server, set bridge's dpid and it's controller
				//then create entryIp and exitIp
				for(int i=0; i<chainConfig.bridges.size(); i++){
					agent.createBridge(chainConfig.bridges.get(i));
					agent.setBridgeDpid(chainConfig.bridges.get(i), 
							            hostServer.serviceChainDpidMap.get(chainName).get(i));
					agent.setController(chainConfig.bridges.get(i), hostServer.controllerConfig.managementIp);
					
					if(i==0){
						//create an entry ip for the first bridge.
						agent.addPort(chainConfig.bridges.get(i), "entry", hostServer.entryPort);
						agent.upPort("entry", hostServer.entryIp);
						hostServer.entryMac = agent.getMac(chainConfig.bridges.get(i), "entry");
						agent.addPatchPort(chainConfig.bridges.get(i), hostServer.frontPortName, hostServer.patchPort, 
								hostServer.rearPortName);
						
					}
					if(i==chainConfig.bridges.size()-1){
						//create an exit ip for the last bridge
						agent.addPort(chainConfig.bridges.get(i), "exit", hostServer.exitPort);
						agent.upPort("exit", hostServer.exitIp);
						hostServer.exitMac = agent.getMac(chainConfig.bridges.get(i), "exit");
						agent.addPatchPort(chainConfig.bridges.get(i), hostServer.rearPortName, hostServer.patchPort, 
								hostServer.frontPortName);
					}
				}
				
				//upload base vm image files
				for(int i=0; i<chainConfig.stages.size(); i++){
					baseImgList.add(chainConfig.getImgNameForStage(i));
					if(!agent.fileExistInDir(hostServer.hostServerConfig.imgDir, chainConfig.getImgNameForStage(i))){
						String imgPath = hostServer.controllerConfig.imgDir+"/"+chainConfig.getImgNameForStage(i);
						String remotePath = hostServer.hostServerConfig.imgDir+"/"+chainConfig.getImgNameForStage(i);
						agent.uploadFile(imgPath, remotePath);
					}
				}
				
				//create management network
				if(agent.networkExist(chainConfig.getManagementNetwork())){
					agent.deleteNetwork(chainConfig.getManagementNetwork());
				}
				String localMNetXMLFile = constructNetworkXmlFile(hostServer.controllerConfig,
							              chainName, chainConfig.getManagementNetwork(),
							              hostServer.serviceChainMNetworkMap.get(chainName));
				String remoteMNetXMLFile = hostServer.hostServerConfig.xmlDir+"/"+
										   chainConfig.getManagementNetwork();
				agent.uploadFile(localMNetXMLFile, remoteMNetXMLFile);
				agent.createNetworkFromXml(remoteMNetXMLFile);
				
				//create operational network if it's control plane
				if(chainConfig.getOperationNetwork()!="nil"){
					if(agent.networkExist(chainConfig.getOperationNetwork())){
						agent.deleteNetwork(chainConfig.getOperationNetwork());
					}
					String localMNetXMLFilz = constructNetworkXmlFile(hostServer.controllerConfig,
								              chainName, chainConfig.getOperationNetwork(),
								              hostServer.serviceChainONetworkMap.get(chainName));
					String remoteMNetXMLFilz = hostServer.hostServerConfig.xmlDir+"/"+
											   chainConfig.getOperationNetwork();
					agent.uploadFile(localMNetXMLFilz, remoteMNetXMLFilz);
					agent.createNetworkFromXml(remoteMNetXMLFilz);
				}
			}
			
			//remove previous generated vm images
			String[] unusedImgArray = agent.createSelectedRemoveList(hostServer.hostServerConfig.imgDir, baseImgList);
			for(int i=0; i<unusedImgArray.length; i++){
				agent.removeFile(hostServer.hostServerConfig.imgDir+"/"+unusedImgArray[i]);
			}
			
			agent.disconnect();
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
	
	//create new vm on a given host server according to 
	//the CreateVmRequest
	private void createVm(CreateVmRequest request){
		
		//acquire VmInstance, create xml file describing the new vm,
		//set remove file names
		VmInstance vmInstance = request.getVmInstance();
		String localXmlFile = constructLocalXmlFile(vmInstance);
		String remoteXmlFile = vmInstance.hostServerConfig.xmlDir+"/"+vmInstance.vmName;
		String remoteImgFile = vmInstance.hostServerConfig.imgDir+"/"+vmInstance.vmName;
		String remoteBaseImgFile = vmInstance.hostServerConfig.imgDir+"/"+
		                   vmInstance.serviceChainConfig.getImgNameForStage(vmInstance.stageIndex);
		
		//this is the actual procedure to create vm on remote host server
		HostAgent agent = new HostAgent(vmInstance.hostServerConfig);
		try{
			
			//agent connects to host server through ssh
			//upload vm xml file to host server
			//create vm image by copying base image
			//create vm from the new xml file
			//finally query the ovs ports for the vm and 
			//set ovs ports in VmInstance
			//reply to the actor that sends the CreateVmRequest
			logger.info("start creating node "+vmInstance.managementIp);
			agent.connect();
			agent.uploadFile(localXmlFile, remoteXmlFile);
			agent.copyFile(remoteBaseImgFile, remoteImgFile);
			Thread.sleep(1000);
			agent.createVMFromXml(remoteXmlFile);
			int[] portList = new int[vmInstance.macList.size()];
			for(int i=0; i<vmInstance.macList.size(); i++){
				String mac = vmInstance.macList.get(i);
				String portMac = "fe:"+mac.substring(3);
				int portNum = agent.getPort(vmInstance.macBridgeMap.get(mac), 
						                    portMac);
				portList[i] = portNum;
			}
			vmInstance.setPort(portList);
			agent.disconnect();
			logger.info("finish creating node "+vmInstance.managementIp);
			CreateVmReply reply = new CreateVmReply(this.getId(), request, true);
			this.mh.sendTo(reply.getRequest().getSourceId(), reply);
			Thread.sleep(5*1000);
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
	
	//create a network xml file for host server to use
	private String constructNetworkXmlFile(ControllerConfig controllerConfig, String chainName,
			String networkName, FakeDhcpAllocator dhcpAllocator){
		Document doc;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		String networkXmlTemplateFile = controllerConfig.xmlDir+"/"+
							controllerConfig.networkTemplateName;
		String localXmlFile = controllerConfig.xmlDir+"/"+networkName+
							dhcpAllocator.bridgeIp;

		try{
			DocumentBuilder db = dbf.newDocumentBuilder();
			doc = db.parse(new File(networkXmlTemplateFile));
			doc.getDocumentElement().normalize();

			Node name = doc.getElementsByTagName("name").item(0);
			name.setTextContent(networkName);

			/*Node forward = doc.getElementsByTagName("forward").item(0);
			Element eForward = (Element)forward;
			eForward.setAttribute("dev", "eth2");

			Node nInterface = eForward.getElementsByTagName("interface").item(0);
			Element eInterface = (Element)nInterface;
			eInterface.setAttribute("dev", "eth2");*/

			Node bridge = doc.getElementsByTagName("bridge").item(0);
			Element eBridge = (Element)bridge;
			String baseIp = new String(dhcpAllocator.bridgeIp);
			int firstDot = baseIp.indexOf('.');
			int secondDot = baseIp.indexOf('.', firstDot+1);
			int thirdDot = baseIp.indexOf('.', secondDot+1);
			eBridge.setAttribute("name", "virbr"+baseIp.substring(secondDot+1, thirdDot));

			Node mac = doc.getElementsByTagName("mac").item(0);
			Element eMac = (Element)mac;
			eMac.setAttribute("address", dhcpAllocator.bridgeMac);

			Node ip = doc.getElementsByTagName("ip").item(0);
			Element eIp = (Element)ip;
			eIp.setAttribute("address", dhcpAllocator.bridgeIp);

			Node dhcp = eIp.getElementsByTagName("dhcp").item(0);
			Element eDhcp = (Element)dhcp;
			Node range = eDhcp.getElementsByTagName("range").item(0);
			Element eRange = (Element)range;
			eRange.setAttribute("start", dhcpAllocator.startIp);
			eRange.setAttribute("end", dhcpAllocator.endIp);

			HashMap<String, String> macIpMap = dhcpAllocator.getMacIpMap();
			int i=0;
			for(String key_mac : macIpMap.keySet()){
				String value_ip = macIpMap.get(key_mac);

				Element eHost = doc.createElement("host");
				eHost.setAttribute("mac", key_mac);
				eHost.setAttribute("name", "host"+new Integer(i).toString());
				i = i+1;
				eHost.setAttribute("ip", value_ip);

				eDhcp.appendChild(eHost);
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
	
	//create vm xml file for vm to use
	private String constructLocalXmlFile(VmInstance vmInstance){
		Document doc;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		
		String vmName = vmInstance.vmName;
		StageVmInfo vmInfo = vmInstance.getStageVmInfo();
		String xmlTemplateFile =vmInstance.controllerConfig.xmlDir+"/"+
						        vmInstance.controllerConfig.xmlTemplateName;
		String destImgFile = vmInstance.hostServerConfig.imgDir+"/"+vmName;
		String localXmlFile = vmInstance.controllerConfig.xmlDir+"/"+vmName;
		
		try{
			DocumentBuilder db = dbf.newDocumentBuilder();
			doc = db.parse(new File(xmlTemplateFile));
			doc.getDocumentElement().normalize();
			
			Node devices = doc.getElementsByTagName("devices").item(0);
			
			Node name = doc.getElementsByTagName("name").item(0);
			name.setTextContent(vmName);
			
			Node memory = doc.getElementsByTagName("memory").item(0);
			memory.setTextContent(new Integer(vmInfo.mem*1024).toString());
			
			Node currentMemory = doc.getElementsByTagName("currentMemory").item(0);
			currentMemory.setTextContent(new Integer(vmInfo.mem*1024).toString());
			
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
			
			Element eMInterface = doc.createElement("interface");
			eMInterface.setAttribute("type", "network");
			
			Element eMMac = doc.createElement("mac");
			eMMac.setAttribute("address", vmInstance.managementMac);
			eMInterface.appendChild(eMMac);
			
			Element eMSource = doc.createElement("source");
			eMSource.setAttribute("network", vmInstance.serviceChainConfig.getManagementNetwork());
			eMInterface.appendChild(eMSource);
			
			Element eMModle = doc.createElement("model");
			eMModle.setAttribute("type", "virtio");
			eMInterface.appendChild(eMModle);
			
			devices.appendChild(eMInterface);
	
			for(String macAddr : vmInstance.macList){
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
			
			if(vmInstance.operationIp!="nil"){
				Element eOInterface = doc.createElement("interface");
				eOInterface.setAttribute("type", "network");
				
				Element eOMac = doc.createElement("mac");
				eOMac.setAttribute("address", vmInstance.operationMac);
				eOInterface.appendChild(eOMac);
				
				Element eOSource = doc.createElement("source");
				eOSource.setAttribute("network", vmInstance.serviceChainConfig.getOperationNetwork());
				eOInterface.appendChild(eOSource);
				
				Element eOModle = doc.createElement("model");
				eOModle.setAttribute("type", "virtio");
				eOInterface.appendChild(eOModle);
				
				devices.appendChild(eOInterface);
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
	
	//when receiving DestroyVmRequest, destroy the vm,
	//delete its xml file and image file, reply to the sender
	private void destroyVm(DestroyVmRequest request){
		VmInstance vmInstance = request.getVmInstance();
		String remoteXmlPath = vmInstance.hostServerConfig.xmlDir+"/"+vmInstance.vmName;
		String remoteImgPath = vmInstance.hostServerConfig.imgDir+"/"+vmInstance.vmName;
		HostAgent agent = new HostAgent(vmInstance.hostServerConfig);
		try{
			agent.connect();
			agent.destroyVm(vmInstance.vmName);
			agent.removeFile(remoteXmlPath);
			agent.removeFile(remoteImgPath);
			agent.disconnect();
			DestroyVmReply reply = new DestroyVmReply(this.getId(), request, true);
			this.mh.sendTo(request.getSourceId(), reply);
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
}
