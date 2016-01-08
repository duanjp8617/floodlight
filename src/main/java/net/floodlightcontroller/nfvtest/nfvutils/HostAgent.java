package net.floodlightcontroller.nfvtest.nfvutils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

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

import java.util.List;

public class HostAgent{
	final String managementIp;
	final String userName;
	final String passWord;
	final SSHClient sshClient;
	
	public HostAgent(HostServerConfig hostConfig){
		this.managementIp = hostConfig.managementIp;
		this.userName = hostConfig.userName;
		this.passWord = hostConfig.passWord;
		sshClient = new SSHClient();
	}
	
	public void connect() throws
	   			   IOException, UserAuthException, TransportException{
		sshClient.addHostKeyVerifier(
                	new HostKeyVerifier() {
                    		@Override
                    		public boolean verify(String s, int i, PublicKey publicKey) {
                        		return true;
                    		}	

                });		
		sshClient.connect(this.managementIp);
		sshClient.authPassword(this.userName, this.passWord);
	}
	
	public void disconnect() throws IOException{
		sshClient.disconnect();
	}
	
	public boolean createBridge(String bridgeName) throws
				   IOException, UserAuthException, TransportException{
		boolean returnVal = false;
		
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("sudo ovs-vsctl br-exists " + bridgeName);
		command.join(2, TimeUnit.SECONDS);
		
		if(command.getExitStatus().intValue() == 2){
			final Session session_1 = sshClient.startSession();
			final Session.Command command_1 = session_1.exec("sudo ovs-vsctl add-br " + bridgeName);
			command_1.join(2, TimeUnit.SECONDS);
			if(command_1.getExitStatus().intValue()==0){
				returnVal = true;
			}
			else{
				returnVal = false;
			}
			session_1.close();
		}
		else if(command.getExitStatus().intValue() == 0){
			returnVal = true;
		}
		else{
			returnVal = false;
		}
		
		session.close();
		return returnVal;
	}
	
	public boolean setBridgeDpid(String bridgeName, String dpid) throws
	   				IOException, UserAuthException, TransportException{
		
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("sudo ovs-vsctl set bridge "+
													 bridgeName + " other-config:hwaddr=" +
				                                     dpid);
		command.join(2, TimeUnit.SECONDS);

		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
		}
	}
	
	public int getPort(String bridgeName, String macAddress) throws
				   IOException, UserAuthException, TransportException{

		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("sudo ovs-ofctl show "+ bridgeName +
										" | grep "+macAddress);
		
		command.join(2, TimeUnit.SECONDS);
		
		if(command.getExitStatus().intValue()==0){
			String result = IOUtils.readFully(command.getInputStream()).toString();
			
			int i = 0;
			while (!Character.isDigit(result.charAt(i))) i++;
			
			String port = result.substring(i, result.indexOf('('));
			System.out.println(port);
			session.close();
			return Integer.parseInt(port);
		}
		else{
			session.close();
			return -10;
		}
	}
	
	public boolean setController(String bridgeName, String controllerIp) throws
		IOException, UserAuthException, TransportException{

			final Session session = sshClient.startSession();
			final Session.Command command = session.exec("sudo ovs-vsctl set-controller "+
												bridgeName+" tcp:"+controllerIp+":6653");

			command.join(2, TimeUnit.SECONDS);

			if(command.getExitStatus().intValue()==0){
				session.close();
				return true;
			}
			else{
				session.close();
				return false;
			}
	}
	
	public boolean networkExist(String networkName) throws
	   			IOException, UserAuthException, TransportException{

		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("virsh net-list "+
							" | grep " + networkName );

		command.join(2, TimeUnit.SECONDS);

		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
		}
	}
	
	public boolean deleteNetwork(String networkName) throws
		IOException, UserAuthException, TransportException{

		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("virsh net-destroy "+ networkName );

		command.join(2, TimeUnit.SECONDS);

		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
		}
	}
	
	public boolean createNetworkFromXml(String remoteXmlFilePath) throws
		IOException, UserAuthException, TransportException{

		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("virsh net-create "+ remoteXmlFilePath );

		command.join(2, TimeUnit.SECONDS);

		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
		}
	}
	
	public boolean createVMFromXml(String remoteXmlFilePath) throws
				   IOException, UserAuthException, TransportException{
		
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("virsh create "+remoteXmlFilePath);
		command.join(15, TimeUnit.SECONDS);
		
		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
		}
	}
	
	public boolean createDir(String remoteDir) throws
	   				IOException, UserAuthException, TransportException{
		
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("mkdir " + remoteDir);
		command.join(2, TimeUnit.SECONDS);
		
		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
		}
	}
	
	public void uploadFile(String localFilePath, String remoteFilePath) throws
		IOException, UserAuthException, TransportException{
		
		sshClient.newSCPFileTransfer().upload(new FileSystemFile(localFilePath), remoteFilePath);
	}
	
	public boolean copyFile(String remoteSrcFile, String remoteDstFile) throws
		IOException, UserAuthException, TransportException{
		
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("cp "+remoteSrcFile+" "+remoteDstFile);
		command.join(15, TimeUnit.SECONDS);
		
		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
		}
	}
	
	public boolean removeFilesFromDir(String remoteDir) throws
		IOException, UserAuthException, TransportException{
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("rm -f "+remoteDir+"/*");
		command.join(2, TimeUnit.SECONDS);
		
		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
		}
	}
	
	public boolean fileExistInDir(String remoteDir, String fileName) throws
		IOException, UserAuthException, TransportException{
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("ls -1 "+remoteDir+" | grep "+fileName);
		command.join(2, TimeUnit.SECONDS);
	
		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
		}
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
		
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("ls -1 "+remoteDir+" | grep -E -v '"+argument+"'");
		command.join(2, TimeUnit.SECONDS);
		
		if(command.getExitStatus().intValue()==0){
			session.close();
			String result = IOUtils.readFully(command.getInputStream()).toString();
			String[] resultArray = result.split("\n");
			return resultArray;
		}
		else{
			session.close();
			String[] resultArray = new String[0];
			return resultArray;
		}
	}
	
	public boolean removeFile(String remoteFilePath)throws
		IOException, UserAuthException, TransportException{
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("rm -f "+remoteFilePath);
		command.join(2, TimeUnit.SECONDS);
	
		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
		}
	}
	
	public boolean destroyVm(String vmName)throws
		IOException, UserAuthException, TransportException{
		
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("virsh destroy "+vmName);
		command.join(60, TimeUnit.SECONDS);

		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
		
		}
	}
	
	public int createTunnelTo(HostServer src, HostServer dst, int vni)throws
		IOException, UserAuthException, TransportException{
		//The agent is connected to server src.
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
		
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec(strCmd);
		command.join(10, TimeUnit.SECONDS);
	
		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
	
		}
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
			final Session session = sshClient.startSession();
			final Session.Command command = session.exec(strCmd);
			command.join(10, TimeUnit.SECONDS);
	
			if(command.getExitStatus().intValue()==0){
				session.close();
				return true;
			}
			else{
				session.close();
				return false;
		
			}
		}
		return false;
	}
	
	public boolean addPatchPort(String bridgeName, String localPortName, int localPortNum, String remotePortName)throws
		IOException, UserAuthException, TransportException{
	
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("sudo ovs-vsctl add-port "+bridgeName+" "+localPortName+
				" -- set interface "+localPortName+" type=patch ofport_request="+new Integer(localPortNum).toString()+
				" options:peer="+remotePortName);
		command.join(60, TimeUnit.SECONDS);
	
		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
	
		}
	}
	
	public boolean addFlow(String bridgeName, int inPort, int outPort)throws
		IOException, UserAuthException, TransportException{
	
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("sudo ovs-ofctl add-flow "+bridgeName+
				" in_port="+Integer.toString(inPort)+" actions=output:"+Integer.toString(outPort));
		command.join(60, TimeUnit.SECONDS);
	
		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
	
		}
	}
	
	public boolean addPort(String bridgeName, String port, int ofPort)throws
		IOException, UserAuthException, TransportException{
	
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("sudo ovs-vsctl add-port "+bridgeName+" "
												 +port+" -- set interface "+port
												 +" type=internal ofport_request="
												 +new Integer(ofPort).toString());
		command.join(60, TimeUnit.SECONDS);
	
		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
	
		}
	}
	
	public boolean upPort(String port, String ip)throws
		IOException, UserAuthException, TransportException{
	
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("sudo ifconfig "+port+" "+ip+" netmask 255.255.255.0 up");
		command.join(60, TimeUnit.SECONDS);
	
		if(command.getExitStatus().intValue()==0){
			session.close();
			return true;
		}
		else{
			session.close();
			return false;
	
		}
	}
	
	public String getMac(String bridgeName, String port) throws
	   	IOException, UserAuthException, TransportException{
	
		final Session session = sshClient.startSession();
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
		}
	}
	
}
