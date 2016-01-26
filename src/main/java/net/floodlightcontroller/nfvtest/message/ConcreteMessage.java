package net.floodlightcontroller.nfvtest.message;
import java.util.ArrayList;
import java.util.Map;

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
		
		public AllocateVmRequest(String sourceId, String chainName, int stageIndex){
			this.sourceId = sourceId;
			this.chainName = chainName;
			this.stageIndex = stageIndex;
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
	
	//The following messages are sent to SubscriberConnector for processing.
	static public class SubConnRequest extends Message{
		private final String sourceId;
		private final String managementIp;
		private final String port1;
		private final String port2;
		private final VmInstance vmInstance;
		private final Message originalMessage;
		
		public SubConnRequest(String sourceId, String managementIp, String port1, String port2,
							  VmInstance vmInstance, Message originalMessage){
			this.sourceId = sourceId;
			this.managementIp = managementIp;
			this.port1 = port1;
			this.port2 = port2;
			this.vmInstance = vmInstance;
			this.originalMessage = originalMessage;
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
		
		public Message getOriginalMessage(){
			return this.originalMessage;
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
		
		public DNSUpdateRequest(String sourceId, String domainName, String ipAddress, String addOrDelete){
			this.sourceId = sourceId;
			this.domainName = domainName;
			this.ipAddress = ipAddress;
			this.addOrDelete = addOrDelete;
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
	}
	
	static public class ProactiveScalingRequest extends Message{
		private final int localCpProvision[];
		private final int localDpProvision[];
		private final int dpPaths[][][];
		private final String sourceId;
		
		public ProactiveScalingRequest(String sourceId, int[] localCpProvision, int[] localDpProvision, 
				int[][][] dpPaths){
			this.localCpProvision = localCpProvision;
			this.localDpProvision = localDpProvision;
			this.sourceId = sourceId;
			this.dpPaths = dpPaths;
		}
		
		public String getSourceId(){
			return sourceId;
		}
		
		public int[] getLocalCpProvision(){
			return localCpProvision;
		}
		
		public int[] getLocalDpProvision(){
			return localDpProvision;
		}
		
		public int[][][] getDpPaths(){
			return this.dpPaths;
		}
		
	}
	
	static public class NewProactiveIntervalRequest extends Message {
		private final String sourceId;
		
		public NewProactiveIntervalRequest(String sourceId){
			this.sourceId = sourceId;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
	}
	
	static public class ProactiveScalingStartRequest extends Message {
		private final String sourceId;
		
		public ProactiveScalingStartRequest(String sourceId){
			this.sourceId = sourceId;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
	}
	
	static public class LocalControllerNotification extends Message {
		private final String sourceId;
		private final int dcIndex;
		private final int dcNum;
		
		public LocalControllerNotification(String sourceId, int dcIndex, int dcNum){
			this.sourceId = sourceId;
			this.dcIndex = dcIndex;
			this.dcNum = dcNum;
		}
		
		public String getSourceId(){
			return this.sourceId;
		}
		
		public int getDcIndex(){
			return this.dcIndex;
		}
		
		public int getDcNum(){
			return this.dcNum;
		}
	}
	
	static public class CreateInterDcTunnelRequest extends Message{
		public final String sourceId;
		
		public final int srcDcIndex;
		public final String srcIp;
		
		public final int dstDcIndex;
		public final String dstIp;
		
		public final int interDcVniIndex;
		public final int tunnelPortNum;
		public final int baseVniIndex;
		
		public final int dcNum;
		
		public CreateInterDcTunnelRequest(String sourceId, int srcDcIndex, String srcIp,
				int dstDcIndex, String dstIp, int interDcVniIndex, int tunnelPortNum, int baseVniIndex, int dcNum){
			this.sourceId = sourceId;
			this.srcDcIndex = srcDcIndex;
			this.srcIp = srcIp;
			this.dstDcIndex = dstDcIndex;
			this.dstIp = dstIp;
			this.interDcVniIndex = interDcVniIndex;
			this.tunnelPortNum = tunnelPortNum; 
			this.baseVniIndex = baseVniIndex;
			this.dcNum = dcNum;
		}
	}
	
	static public class CreateInterDcTunnelMash extends Message{
		public final String sourceId;
		
		public final String srcIp;
		public final int globalBaseVni;
		
		public final Map<String, Integer> localcIndexMap;
		
		public CreateInterDcTunnelMash(String sourceId, String srcIp, int globalBaseVni, Map<String, Integer> localcIndexMap){
			this.sourceId = sourceId;
			this.srcIp = srcIp;
			this.globalBaseVni = globalBaseVni;
			this.localcIndexMap = localcIndexMap;
		}
	}
	
	static public class CreateInterDcTunnelMashReply extends Message{
		public final String sourceId;
		
		public CreateInterDcTunnelMashReply(String sourceId){
			this.sourceId = sourceId;
		}
	}
}
