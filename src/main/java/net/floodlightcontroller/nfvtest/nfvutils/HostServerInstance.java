package net.floodlightcontroller.nfvtest.nfvutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.connection.channel.direct.Session;

import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVException;

public class HostServerInstance{
	final String managementIp;
	final String internalIp;
	final String publicIp;
	
	final int  cpuCapacity;     //in # of cores
	final long memoryCapacity; //in Mbytes
	final long storageCapacity; //in Mbytes
	final long interfaceSpeed; //in Gbps
	
	final String userName;
	final String passWord;
	
	final SSHClient sshClient;
	
	HostServerInstance(String managementIp, String internalIp, String publicIp,
			   		   int cpuCapacity, long memoryCapacity, long storageCapacity,
			           long interfaceSpeed, String userName, String passWord){
		this.managementIp = managementIp;
		this.internalIp = internalIp;
		this.publicIp = publicIp;
		this.cpuCapacity = cpuCapacity;
		this.memoryCapacity = memoryCapacity;
		this.storageCapacity = storageCapacity;
		this.interfaceSpeed = interfaceSpeed;
		this.userName = userName;
		this.passWord = passWord;
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
	
}
