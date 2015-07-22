package net.floodlightcontroller.nfvtest.nfvslaveservice;

import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.nfvutils.HostAgent;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.ServiceChainConfig;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.StageVmInfo;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer.VmInstance;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VmWorker extends MessageProcessor{
	
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
			agent.connect();
			agent.createDir(hostServer.hostServerConfig.homeDir);
			agent.createDir(hostServer.hostServerConfig.xmlDir);
			agent.createDir(hostServer.hostServerConfig.imgDir);
			agent.removeFilesFromDir(hostServer.hostServerConfig.xmlDir);
			
			ArrayList<String> baseImgList = new ArrayList<String>();
			for(String chainName : hostServer.serviceChainConfigMap.keySet()){
				ServiceChainConfig chainConfig = hostServer.serviceChainConfigMap.get(chainName);
				for(int i=0; i<chainConfig.bridges.size(); i++){
					agent.createBridge(chainConfig.bridges.get(i));
				}
				for(int i=0; i<chainConfig.stages.size(); i++){
					baseImgList.add(chainConfig.getImgNameForStage(i));
					if(!agent.fileExistInDir(hostServer.hostServerConfig.imgDir, chainConfig.getImgNameForStage(i))){
						String imgPath = hostServer.controllerConfig.imgDir+"/"+chainConfig.getImgNameForStage(i);
						String remotePath = hostServer.hostServerConfig.imgDir+"/"+chainConfig.getImgNameForStage(i);
						agent.uploadFile(imgPath, remotePath);
					}
				}
			}
			
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
	
	private void createVm(CreateVmRequest request){
		VmInstance vmInstance = request.getVmInstance();
		String localXmlFile = constructLocalXmlFile(vmInstance);
		String remoteXmlFile = vmInstance.hostServerConfig.xmlDir+"/"+vmInstance.vmName;
		String remoteImgFile = vmInstance.hostServerConfig.imgDir+"/"+vmInstance.vmName;
		String remoteBaseImgFile = vmInstance.hostServerConfig.imgDir+"/"+
		                   vmInstance.serviceChainConfig.getImgNameForStage(vmInstance.stageIndex);
		
		HostAgent agent = new HostAgent(vmInstance.hostServerConfig);
		try{
			agent.connect();
			agent.uploadFile(localXmlFile, remoteXmlFile);
			agent.copyFile(remoteBaseImgFile, remoteImgFile);
			agent.createVMFromXml(remoteXmlFile);
			agent.disconnect();
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
	
	
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
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
}
