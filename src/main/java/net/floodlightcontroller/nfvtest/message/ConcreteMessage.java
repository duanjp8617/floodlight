package net.floodlightcontroller.nfvtest.message;
import java.util.ArrayList;

import org.zeromq.ZMQ.Socket;

import net.floodlightcontroller.nfvtest.message.Message;

import net.floodlightcontroller.nfvtest.nfvutils.HostServer.VmInstance;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVServiceChain;

public class ConcreteMessage {
	/*
	 * General messages received by all processor for termination.
	 */
	static public class KillSelfRequest extends Message{
		private final String sourceId;
		
		public KillSelfRequest(String sourceId){
			this.sourceId = sourceId;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
	}
	
	/*
	 * The following requests are sent to VmWorker processor to prepare the 
	 * NFV environment and create VMs.
	 * The following replies are generated by the VmWorker processor to respond
	 * to the requests sent by other processors.
	 */
	
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
	
	static public class CreateVmReply extends Message {
		private final String sourceId;
		private final boolean successful;
		private final CreateVmRequest request;
		
		public CreateVmReply(String sourceId, CreateVmRequest request, boolean isRequestSuccessful){
			this.sourceId = sourceId;
			this.successful = isRequestSuccessful;
			this.request = request;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public CreateVmRequest getRequest(){
			return request;
		}
		
		public boolean getSuccessful(){
			return this.successful;
		}
		
		public VmInstance getVmInstance(){
			return this.request.getVmInstance();
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
	
	static public class DestroyVmReply extends Message {
		private final String sourceId;
		private final DestroyVmRequest request;
		private final boolean isSuccessful;
		
		public DestroyVmReply(String sourceId, DestroyVmRequest request, boolean isSuccessful){
			this.sourceId = sourceId;
			this.request = request;
			this.isSuccessful = isSuccessful;
		}
		
		public String getSourceId(){
			return sourceId;
		}
		
		public DestroyVmRequest getRequest(){
			return this.request;
		}
		
		public boolean getSuccessful(){
			return this.isSuccessful;
		}
	}
	
	/*
	 * The following requests are sent to the VmAllocator processor.
	 */
	static public class AllocateVmRequest extends Message {
		private final String sourceId;
		private final String chainName;
		private final int stageIndex;
		private final boolean isBufferNode;
		private final int dcIndex;
		
		public AllocateVmRequest(String sourceId, String chainName, int stageIndex, 
				                 boolean isBufferNode, int dcIndex){
			this.sourceId = sourceId;
			this.chainName = chainName;
			this.stageIndex = stageIndex;
			this.isBufferNode = isBufferNode;
			this.dcIndex = dcIndex;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public String getChainName(){
			return this.chainName;
		}
		
		public int getStageIndex(){
			return this.stageIndex;
		}
		
		public boolean getIsBufferNode(){
			return this.isBufferNode;
		}
		
		public int getDcIndex(){
			return this.dcIndex;
		}
	}
	
	static public class AllocateVmReply extends Message {
		private final String sourceId;
		private final VmInstance vmInstance;
		private final AllocateVmRequest request;
		
		public AllocateVmReply(String sourceId, VmInstance vmInstance, AllocateVmRequest request){
			this.sourceId = sourceId;
			this.vmInstance = vmInstance;
			this.request = request;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public VmInstance getVmInstance(){
			return this.vmInstance;
		}
		
		public AllocateVmRequest getAllocateVmRequest(){
			return this.request;
		}
	}
	
	static public class AddHostServerRequest extends Message {
		private final String sourceId;
		private final HostServer hostServer;
		
		public AddHostServerRequest(String sourceId, HostServer hostServer){
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
	
	static public class DeallocateVmRequest extends Message {
		private final String sourceId;
		private final VmInstance vmInstance;
		
		public DeallocateVmRequest(String sourceId, VmInstance vmInstance){
			this.sourceId = sourceId;
			this.vmInstance = vmInstance;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public VmInstance getVmInstance(){
			return this.vmInstance;
		}
	}
	
	//The following messages are sent to ServiceChainHandler for processing.
	static public class InitServiceChainRequset extends Message{
		private final String sourceId;
		private final NFVServiceChain serviceChain;
		
		public InitServiceChainRequset(String sourceId, NFVServiceChain serviceChain){
			this.sourceId = sourceId;
			this.serviceChain = serviceChain;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public NFVServiceChain getServiceChain(){
			return this.serviceChain;
		}
	}
	
	static public class StatUpdateRequest extends Message{
		private final String sourceId;
		private final ArrayList<String> statList;
		private final String managementIp;
		
		public StatUpdateRequest(String sourceId, String managementIp, ArrayList<String> statList){
			this.sourceId = sourceId;
			this.managementIp = managementIp;
			this.statList = statList;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public String getManagementIp(){
			return this.managementIp;
		}
		
		public ArrayList<String> getStatList(){
			return this.statList;
		}
	}
	
	static public class ServerToChainHandlerRequest extends Message{
		private final String sourceId;
		private final HostServer hostServer;
		
		public ServerToChainHandlerRequest(String sourceId, HostServer hostServer){
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
	
	
	//The following messages are sent to SubscriberConnector for processing.
	static public class SubConnRequest extends Message{
		private final String sourceId;
		private final String managementIp;
		private final String port1;
		private final String port2;
		private final VmInstance vmInstance;
		
		public SubConnRequest(String sourceId, String managementIp, String port1, String port2,
							  VmInstance vmInstance){
			this.sourceId = sourceId;
			this.managementIp = managementIp;
			this.port1 = port1;
			this.port2 = port2;
			this.vmInstance = vmInstance;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public String getManagementIp(){
			return this.managementIp;
		}
		
		public String getPort1(){
			return this.port1;
		}
		
		public String getPort2(){
			return this.port2;
		}
		
		public VmInstance getVmInstance(){
			return this.vmInstance;
		}
	}
	
	static public class SubConnReply extends Message{
		private final String sourceId;
		private final SubConnRequest request;
		private final Socket subscriber1;
		private final Socket subscriber2;
			
		public SubConnReply(String sourceId, SubConnRequest request, Socket subscriber1, 
					        Socket subscriber2){
			this.sourceId = sourceId;
			this.request = request;
			this.subscriber1 = subscriber1;
			this.subscriber2 = subscriber2;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public SubConnRequest getSubConnRequest(){
			return this.request;
		}
		
		public Socket getSubscriber1(){
			return this.subscriber1;
		}
		
		public Socket getSubscriber2(){
			return this.subscriber2;
		}
	}
	
	/*
	 * The following requests are sent to the DNSUpdator processor.
	 */
	static public class DNSUpdateRequest extends Message{
		private final String sourceId;
		private final String domainName;
		private final String ipAddress;
		private final String addOrDelete;
		private final Socket socket1;
		private final Socket socket2;
		private final VmInstance vmInstance;
		
		public DNSUpdateRequest(String sourceId, String domainName, String ipAddress, String addOrDelete,
				                Socket socket1, Socket socket2, VmInstance vmInstance){
			this.sourceId = sourceId;
			this.domainName = domainName;
			this.ipAddress = ipAddress;
			this.addOrDelete = addOrDelete;
			this.socket1 = socket1;
			this.socket2 = socket2;
			this.vmInstance = vmInstance;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public String getDomainName(){
			return this.domainName;
		}
		
		public String getIpAddress(){
			return this.ipAddress;
		}
		
		public String getAddOrDelete(){
			return this.addOrDelete;
		}
		
		public Socket getSocket1(){
			return this.socket1;
		}
		
		public Socket getSocket2(){
			return this.socket2;
		}
		
		public VmInstance getVmInstance(){
			return this.vmInstance;
		}
	}
	
	static public class DNSUpdateReply extends Message{
		private final DNSUpdateRequest request;
		private final String sourceId;
		
		public DNSUpdateReply(String sourceId, DNSUpdateRequest request){
			this.request = request;
			this.sourceId = sourceId;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public DNSUpdateRequest getDNSUpdateReq(){
			return this.request;
		}
	}
	
	static public class DNSRemoveRequest extends Message{
		private final String sourceId;
		private final String domainName;
		private final String ipAddress;
		
		public DNSRemoveRequest(String sourceId, String domainName, String ipAddress){
			this.sourceId = sourceId;
			this.domainName = domainName;
			this.ipAddress = ipAddress;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public String getDomainName(){
			return this.domainName;
		}
		
		public String getIpAddress(){
			return this.ipAddress;
		}
	}
	
	//The following messages are sent from SwitchStatPoller
	static public class DcLinkStat extends Message{
		private final String sourceId;
		private final int size;
		private final float[][] dcSendSpeed;
		private final float[][] dcRecvSpeed;
		
		public DcLinkStat(String sourceId, int size, float[][] dcSendSpeed, float[][] dcRecvSpeed){
			this.sourceId = sourceId;
			this.size = size;
			this.dcSendSpeed = dcSendSpeed;
			this.dcRecvSpeed = dcRecvSpeed;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public int getSize(){
			return this.size;
		}
		
		public float[][] getDcSendSpeed(){
			return this.dcSendSpeed;
		}
		
		public float[][] getDcRecvSpeed(){
			return this.dcRecvSpeed;
		}
	}
}
