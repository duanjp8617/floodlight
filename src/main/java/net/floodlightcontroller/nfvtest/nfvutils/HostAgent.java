package net.floodlightcontroller.nfvtest.nfvutils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.connection.channel.direct.Session;

import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.HostServerConfig;

public class HostAgent{
	final String managementIp;
	final String userName;
	final String passWord;
	final SSHClient sshClient;
	
	HostAgent(HostServerConfig hostConfig){
		this.managementIp = hostConfig.managementIp;
		this.userName = hostConfig.userName;
		this.passWord = hostConfig.passWord;
		sshClient = new SSHClient();
	}
	
	public boolean createBridge(String bridgeName) throws
				   IOException, UserAuthException, TransportException{
		sshClient.loadKnownHosts();
		sshClient.connect(this.managementIp);
		sshClient.authPassword(this.userName, this.passWord);
		boolean returnVal = false;
		
		/*
		 * First obtain root identity.
		 */
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("sudo ovs-vsctl br-exists " + bridgeName);
		command.join(2, TimeUnit.SECONDS);
		
		if(command.getExitStatus().intValue() == 2){
			final Session session_1 = sshClient.startSession();
			final Session.Command command_1 = session_1.exec("sudo ovs-vsctl br-add " + bridgeName);
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
		sshClient.disconnect();
		return returnVal;
	}
	
	public boolean createVMFromXml(String remoteXmlFilePath) throws
				   IOException, UserAuthException, TransportException{
		sshClient.loadKnownHosts();
		sshClient.connect(this.managementIp);
		sshClient.authPassword(this.userName, this.passWord);
		
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("virsh create "+remoteXmlFilePath);
		command.join(2, TimeUnit.SECONDS);
		
		if(command.getExitStatus().intValue()==0){
			session.close();
			sshClient.disconnect();
			return true;
		}
		else{
			session.close();
			sshClient.disconnect();
			return false;
		}
	}
	
	public boolean createDir(String remoteDir) throws
	   				IOException, UserAuthException, TransportException{
		sshClient.loadKnownHosts();
		sshClient.connect(this.managementIp);
		sshClient.authPassword(this.userName, this.passWord);
		
		final Session session = sshClient.startSession();
		final Session.Command command = session.exec("mkdir " + remoteDir);
		command.join(2, TimeUnit.SECONDS);
		
		if(command.getExitStatus().intValue()==0){
			session.close();
			sshClient.disconnect();
			return true;
		}
		else{
			session.close();
			sshClient.disconnect();
			return false;
		}
	}
	
	public void uploadFile(String localFilePath, String remoteFilePath) throws
		IOException, UserAuthException, TransportException{
		sshClient.loadKnownHosts();
		sshClient.connect(this.managementIp);
		sshClient.authPassword(this.userName, this.passWord);
		
		sshClient.newSCPFileTransfer().upload(new FileSystemFile(localFilePath), remoteFilePath);
	}
}
