package net.floodlightcontroller.nfvtest.nfvutils;

import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.*;

import java.io.File;
import java.util.Map;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;


public class VmInstance {
	final int cpu;
	final int mem;
	final int storage;
	
	final String vmName;
	
	final int stageNum;
	final String xmlTemplateFile;
	final String xmlFile;
	final String imgFile;
	final Map<String, String> macIpMap;
	final Map<String, String> macBridgeMap;
	
	VmInstance(ControllerConfig cConfig, HostServerConfig hConfig, 
			   ServiceChainConfig sConfig, int stageNum, String vmName,
			   Map<String, String> macIpMap, Map<String, String> macBridgeMap){
		this.stageNum = stageNum;
		this.vmName = vmName;
		this.macIpMap = macIpMap;
		this.macBridgeMap = macBridgeMap;
		
		this.cpu = sConfig.getStageVmInfo(stageNum).cpu;
		this.mem = sConfig.getStageVmInfo(stageNum).mem;
		this.storage = sConfig.getStageVmInfo(stageNum).storage;
		
		this.xmlTemplateFile = cConfig.xmlDir+"/"+cConfig.xmlTemplateName;
		this.xmlFile = cConfig.xmlDir+"/"+this.vmName+".xml";
		this.imgFile = hConfig.imgDir+ "/"+this.vmName+".img";
	}
	
	public String constructXmlFile(){
		Document doc;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try{
			DocumentBuilder db = dbf.newDocumentBuilder();
			doc = db.parse(new File(this.xmlTemplateFile));
			doc.getDocumentElement().normalize();
			
			Node devices = doc.getElementsByTagName("devices").item(0);
			
			Node name = doc.getElementsByTagName("name").item(0);
			name.setTextContent(this.vmName);
			
			Node memory = doc.getElementsByTagName("memory").item(0);
			memory.setTextContent(new Integer(this.mem).toString());
			
			Node currentMemory = doc.getElementsByTagName("currentMemory").item(0);
			currentMemory.setTextContent(new Integer(this.mem).toString());
			
			Node vcpu = doc.getElementsByTagName("vcpu").item(0);
			vcpu.setTextContent(new Integer(this.cpu).toString());
			
			NodeList diskList = doc.getElementsByTagName("disk");
			for(int i=0; i<diskList.getLength(); i++){
				Node disk = diskList.item(i);
				NamedNodeMap attr = disk.getAttributes();
				Node type = attr.getNamedItem("type");
				
				if(type.getTextContent().equals("file")){
					Element eDisk = (Element)disk;
					Node source = eDisk.getElementsByTagName("source").item(0);
					Element eSource = (Element)source;
					eSource.setAttribute("file", this.imgFile);
				}
			}
	
			for(String macAddr : macBridgeMap.keySet()){
				String bridge = macBridgeMap.get(macAddr);
				
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
			
			File constructedFile = new File(this.xmlFile);
			StreamResult result = new StreamResult(constructedFile);
			DOMSource source = new DOMSource(doc);
			transformer.transform(source, result);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		return this.xmlFile;
	}
}	
