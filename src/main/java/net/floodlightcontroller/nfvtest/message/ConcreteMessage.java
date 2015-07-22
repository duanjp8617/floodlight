package net.floodlightcontroller.nfvtest.message;
import net.floodlightcontroller.nfvtest.message.Message;

import net.floodlightcontroller.nfvtest.nfvutils.HostServer.VmInstance;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;

public class ConcreteMessage {
	
	static public class KillSelfRequest extends Message{
		private final String sourceId;
		
		public KillSelfRequest(String sourceId){
			this.sourceId = sourceId;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
	}
	
	
	static public class CreateVmRequest extends Message {
		private final String sourceId;
		private final VmInstance vmInstance;
		
		public CreateVmRequest(String sourceId, VmInstance vmInstance){
			this.sourceId = sourceId;
			this.vmInstance = vmInstance;
		}
		
		public String getSourceId(){
			return sourceId;
		}
		
		public VmInstance getVmInstance(){
			return vmInstance;
		}
	}
	
	static public class HostInitializationRequest extends Message {
		private final String sourceId;
		private final HostServer hostServer;
		
		public HostInitializationRequest(String sourceId, HostServer hostServer){
			this.sourceId = sourceId;
			this.hostServer = hostServer;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public HostServer getHostServer(){
			return this.hostServer;
		}
	}
	
	static public class DestroyVmRequest extends Message {
		private final String sourceId;
		private final VmInstance vmInstance;
		
		public DestroyVmRequest(String sourceId, VmInstance vmInstance){
			this.sourceId = sourceId;
			this.vmInstance = vmInstance;
		}
		
		public String getSourceId(){
			return sourceId;
		}
		
		public VmInstance getVmInstance(){
			return vmInstance;
		}
	}
}
