package net.floodlightcontroller.nfvtest.nfvutils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.types.IPv4Address;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.*;
import java.security.PublicKey;


import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.HostServerConfig;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.ServiceChainConfig;

import java.util.ArrayList;
import java.util.List;

public class HostAgent{
	final String managementIp;
	final String userName;
	final String passWord;
	
	public HostAgent(HostServerConfig hostConfig){
		this.managementIp = hostConfig.managementIp;
		this.userName = hostConfig.userName;
		this.passWord = hostConfig.passWord;
	}
	
	public void connect() throws
	   			   IOException, UserAuthException, TransportException{
	}
	
	public void disconnect() throws IOException{
	}
	
	public boolean createBridge(String bridgeName) throws
				   IOException, UserAuthException, TransportException{
		
		String cmd = "sudo ovs-vsctl add-br " + bridgeName;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		
        try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
        
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean setBridgeDpid(String bridgeName, String dpid) throws
	   				IOException, UserAuthException, TransportException{
		
		String cmd = "sudo ovs-vsctl set bridge "+bridgeName+" other-config:hwaddr="+dpid;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
        
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public int getPort(String bridgeName, String macAddress) throws
				   IOException, UserAuthException, TransportException{
		
		String cmd = "sudo ovs-ofctl show "+ bridgeName +" | grep "+macAddress;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal == 0){
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String result = r.readLine();
			int i = 0;
			while (!Character.isDigit(result.charAt(i))) i++;
			String port = result.substring(i, result.indexOf('('));
			System.out.println(port);
			return Integer.parseInt(port);
        }
        else{
        	return -10;
        }
	}
	
	public boolean setController(String bridgeName, String controllerIp) throws
		IOException, UserAuthException, TransportException{

		String cmd = "sudo ovs-vsctl set-controller "+bridgeName+" tcp:"+controllerIp+":6653";
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal=0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean networkExist(String networkName) throws
	   			IOException, UserAuthException, TransportException{

		String cmd = "virsh net-list "+" | grep " + networkName;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean deleteNetwork(String networkName) throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "virsh net-destroy "+ networkName;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean createNetworkFromXml(String remoteXmlFilePath) throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "virsh net-create "+ remoteXmlFilePath;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        cmd = "virsh net-start "+ remoteXmlFilePath;
		ProcessBuilder builder1 = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder1.redirectErrorStream(true);
        Process p1 = null;
		try {
			p1 = builder1.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p1.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean createVMFromXml(String remoteXmlFilePath) throws
				   IOException, UserAuthException, TransportException{
		
		String cmd = "virsh create "+remoteXmlFilePath;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean createDir(String remoteDir) throws
	   				IOException, UserAuthException, TransportException{
		
		String cmd = "mkdir " + remoteDir;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public void uploadFile(String localFilePath, String remoteFilePath) throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "cp " + localFilePath + " " + remoteFilePath;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        return;
	}
	
	public boolean copyFile(String remoteSrcFile, String remoteDstFile) throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "cp "+remoteSrcFile+" "+remoteDstFile;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean removeFilesFromDir(String remoteDir) throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "rm -f "+remoteDir+"/*";
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean fileExistInDir(String remoteDir, String fileName) throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "ls -1 "+remoteDir+" | grep "+fileName;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public String[] createSelectedRemoveList(String remoteDir, List<String> fileList)throws
		IOException, UserAuthException, TransportException{
		
		String argument = "";
		for(int i=0; i<fileList.size(); i++){
			if(i<fileList.size()-1){
				argument += (fileList.get(i)+"|");
			}
			else{
				argument += fileList.get(i);
			}
		}
		
		String cmd = "ls -1 "+remoteDir+" | grep -E -v '"+argument+"'";
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
		
        if(returnVal==0){
        	ArrayList<String> array = new ArrayList<String>();
            while(true){
            	String line = r.readLine();
            	if(line == null){
            		break;
            	}
            	else{
            		array.add(line);
            	}
            }
            String[] resultArray = new String[array.size()];
            for(int i=0; i<array.size(); i++){
            	resultArray[i] = array.get(i);
            }
            return resultArray;
        }
        else{
        	String[] resultArray = new String[0];
			return resultArray;
        }
	}
	
	public boolean removeFile(String remoteFilePath)throws
		IOException, UserAuthException, TransportException{

		String cmd = "rm -f "+remoteFilePath;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean destroyVm(String vmName)throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "virsh destroy "+vmName;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public int createTunnelTo(HostServer src, HostServer dst, int vni)throws
		IOException, UserAuthException, TransportException{
		int vniIndex = vni;
		List<String> srcBridgeList = null;
		List<String> dstBridgeList = null;
		
		for(String chainName : src.serviceChainConfigMap.keySet()){
			ServiceChainConfig chainConfig = src.serviceChainConfigMap.get(chainName);
			if(chainConfig.bridges.size()>0){
				srcBridgeList = chainConfig.bridges;
			}
		}
		
		for(String chainName : dst.serviceChainConfigMap.keySet()){
			ServiceChainConfig chainConfig = dst.serviceChainConfigMap.get(chainName);
			if(chainConfig.bridges.size()>0){
				dstBridgeList = chainConfig.bridges;
			}
		}
		
		
		if((srcBridgeList==null)||(dstBridgeList==null)){
			return -1;
		}
		
		String dstIp = dst.hostServerConfig.managementIp;
		src.tunnelPortMap.put(dstIp, new Integer(src.tunnelPort));
		src.portTunnelMap.put(new Integer(src.tunnelPort), dstIp);
		
		for(int i=0; i<srcBridgeList.size(); i++){
			String ovsPortName = "intraDc-"+dstIp+"-vni-"+new Integer(vniIndex).toString();
			this.createTunnelPort(ovsPortName, srcBridgeList.get(i), dstIp, src.tunnelPort, vniIndex);
			vniIndex+=1;
		}
		src.tunnelPort +=1;
		
		
		return vniIndex;
	}
	
	public boolean createTunnelPort(String portName, String bridgeName, String dstIp, int tunnelPort, int vniIndex)throws
		IOException, UserAuthException, TransportException{
		
		String strCmd = "sudo ovs-vsctl add-port "+bridgeName+" "+portName+" -- set interface "+
				portName+" type=vxlan options:remote_ip=\""+dstIp+"\""+" options:key="+
						new Integer(vniIndex).toString()+" ofport_request="+new Integer(tunnelPort).toString();
		
		String cmd = strCmd;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean createRouteTo(HostServer src, HostServer dst)throws
		IOException, UserAuthException, TransportException{
		String dstOperationNetwork = null;
		for(String chainName : src.serviceChainConfigMap.keySet()){
			ServiceChainConfig chainConfig = src.serviceChainConfigMap.get(chainName);
			if(!chainConfig.getOperationNetwork().equals("nil")){
				dstOperationNetwork = chainConfig.getOperationNetwork();
			}
		}
		if(dstOperationNetwork!=null){
			String prefix = dstOperationNetwork.substring(0, dstOperationNetwork.lastIndexOf("."));
			String strCmd = "sudo route add -net "+prefix+".0"+" netmask 255.255.255.0 gw "+
							src.hostServerConfig.managementIp+" dev eth2";
			
			String cmd = strCmd;
			ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
	        builder.redirectErrorStream(true);
	        Process p = null;
	        int returnVal = 0;
			try {
				p = builder.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
	        try {
				returnVal = p.waitFor();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
	        if(returnVal==0)
	        	return true;
	        else
	        	return false;
		}
		return false;
	}
	
	public boolean addPatchPort(String bridgeName, String localPortName, int localPortNum, String remotePortName)throws
		IOException, UserAuthException, TransportException{
	
		String cmd = "sudo ovs-vsctl add-port "+bridgeName+" "+localPortName+
				" -- set interface "+localPortName+" type=patch ofport_request="+new Integer(localPortNum).toString()+
				" options:peer="+remotePortName;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean addEntrySwitchFlow(String bridgeName, int outPort, int inputPort, int dstDcIndex)throws
		IOException, UserAuthException, TransportException{
		byte newDstAddr[] = new byte[4];
		byte mask[] = new byte[4];
		for(int i=0; i<4; i++){
			if((i)==0){
				newDstAddr[i] = (byte)dstDcIndex;
				mask[i] = ((byte)255);
			}
			else{
				newDstAddr[i] = 0;
				mask[i] = ((byte)0);
			}
		}
		
		String cmd = "sudo ovs-ofctl add-flow "+bridgeName+" in_port="+new Integer(inputPort).toString()+
				",ip,nw_dst="+IPv4Address.of(newDstAddr).toString()+"/"+IPv4Address.of(mask).toString()
				+",actions=mod_nw_dst:1.1.1.1,output:"+Integer.toString(outPort);
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean addDpFlow(String bridgeName, int outPort, int dstDcIndex, int stageIndex)throws
		IOException, UserAuthException, TransportException{
		
		byte newDstAddr[] = new byte[4];
		byte mask[] = new byte[4];
		for(int i=0; i<4; i++){
			if((i)==stageIndex){
				newDstAddr[i] = (byte)dstDcIndex;
				mask[i] = ((byte)255);
			}
			else{
				newDstAddr[i] = 0;
				mask[i] = ((byte)0);
			}
		}
		
		String cmd = "sudo ovs-ofctl add-flow "+bridgeName+
				" ip,nw_dst="+IPv4Address.of(newDstAddr).toString()+"/"+IPv4Address.of(mask).toString()
				+",actions=mod_nw_dst:1.1.1.1,output:"+Integer.toString(outPort);
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean addTailFlow(String bridgeName, int outPort)throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "sudo ovs-ofctl add-flow "+bridgeName+
				" ip,nw_dst=0.0.0.9/0.0.0.255,actions=mod_nw_dst:1.1.1.1,output:"+Integer.toString(outPort);
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean addFlow(String bridgeName, int inPort, int outPort)throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "sudo ovs-ofctl add-flow "+bridgeName+
				" in_port="+Integer.toString(inPort)+",actions=output:"+Integer.toString(outPort);
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean addStatFlow(String bridgeName, int inPort, int outPort, int srcDcIndex, int dstDcIndex)throws
		IOException, UserAuthException, TransportException{
	
		int tos = ((srcDcIndex&0x7)<<5)+((dstDcIndex&07)<<2);
		
		String cmd = "sudo ovs-ofctl add-flow "+bridgeName+
				" ip,nw_tos="+new Integer(tos).toString()
				+",actions=output:"+Integer.toString(outPort);
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean addFlowDstMac(String bridgeName, int inPort, int outPort, String dstMac)throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "sudo ovs-ofctl add-flow "+bridgeName+
				" in_port="+Integer.toString(inPort)+",actions=mod_dl_dst:"+dstMac+",output:"+Integer.toString(outPort);
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean createRouteToGateway(String destinationIp, String gatewayIp, String devName)throws
		IOException, UserAuthException, TransportException{
		int thirdDotPos=destinationIp.lastIndexOf(".");
		String firstThree = destinationIp.substring(0, thirdDotPos);
		String subnetAddr = firstThree+".0";
		
		String cmd = "sudo route add -net "+subnetAddr+" netmask 255.255.255.0 gw "+gatewayIp
				+" dev "+devName;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean addPort(String bridgeName, String port, int ofPort)throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "sudo ovs-vsctl add-port "+bridgeName+" "
				 +port+" -- set interface "+port
				 +" type=internal ofport_request="
				 +new Integer(ofPort).toString();
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean removePort(String bridgeName, String port)throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "sudo ovs-vsctl del-port "+bridgeName+" "+port;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public boolean upPort(String port, String ip)throws
		IOException, UserAuthException, TransportException{
		
		String cmd = "sudo ifconfig "+port+" "+ip+" netmask 255.255.255.0 up";
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        if(returnVal==0)
        	return true;
        else
        	return false;
	}
	
	public String getMac(String bridgeName, String port) throws
	   	IOException, UserAuthException, TransportException{
	
		/*final Session session = sshClient.startSession();
		final Session.Command command = session.exec("sudo ovs-ofctl show "+ bridgeName +
							" | grep "+port);
	
		command.join(2, TimeUnit.SECONDS);
	
		if(command.getExitStatus().intValue()==0){
			String result = IOUtils.readFully(command.getInputStream()).toString();
	
			int start = result.indexOf("addr:");
			start += 5;
			String returnVal = result.substring(start, start+17);
			System.out.println(returnVal);
			return returnVal;
		}
		else{
			session.close();
			return "nil";
		}*/
		
		String cmd = "sudo ovs-ofctl show "+ bridgeName +" | grep "+port;
		ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-c", cmd);
        builder.redirectErrorStream(true);
        Process p = null;
        int returnVal = 0;
		try {
			p = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
        try {
			returnVal = p.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        
		
        if(returnVal==0){
        	String result = r.readLine();
        	int start = result.indexOf("addr:");
			start += 5;
			String mac = result.substring(start, start+17);
			System.out.println(mac);
			return mac;
        }
        else{
        	return "nil";
        }
	}
	
}