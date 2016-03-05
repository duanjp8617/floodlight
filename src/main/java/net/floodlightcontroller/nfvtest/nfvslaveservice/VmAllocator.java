package net.floodlightcontroller.nfvtest.nfvslaveservice;
import java.util.concurrent.LinkedBlockingQueue;

import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.nfvutils.HostAgent;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.ServiceChainConfig;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer.*;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.userauth.UserAuthException;
import net.floodlightcontroller.nfvtest.message.Pending;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class VmAllocator extends MessageProcessor {
	public final ArrayList<HostServer> hostServerList;
	private final HashMap<UUID, Pending> pendingMap;
	private int vni;
	public final HashMap<DatapathId, HostServer> dpidHostServerMap;
	public final HashMap<DatapathId, Integer> dpidStageIndexMap;
	private final Logger logger =  LoggerFactory.getLogger(VmAllocator.class);

	public VmAllocator(String id, int baseVni){
		this.id = id;
		this.queue = new LinkedBlockingQueue<Message>();
		this.hostServerList = new ArrayList<HostServer>();
		this.pendingMap = new HashMap<UUID, Pending>();
		this.vni=baseVni;
		this.dpidHostServerMap = new HashMap<DatapathId, HostServer>();
		this.dpidStageIndexMap = new HashMap<DatapathId, Integer>();
	}
	
	
	@Override
	public void run() {
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
		if(m instanceof AllocateVmRequest){
			AllocateVmRequest request = (AllocateVmRequest)m;
			allocateVm(request);
		}
		else if (m instanceof AddHostServerRequest){
			AddHostServerRequest request = (AddHostServerRequest)m;
			addHostServer(request);
		}
		else if(m instanceof CreateVmReply){
			CreateVmReply reply = (CreateVmReply)m;
			handleCreateVmReply(reply);
		}
		else if(m instanceof DeallocateVmRequest){
			DeallocateVmRequest request = (DeallocateVmRequest)m;
			deallocateVm(request);
		}
		else if(m instanceof DestroyVmReply){
			DestroyVmReply reply = (DestroyVmReply)m;
			handleDestroyVmReply(reply);
		}
		else if(m instanceof CreateInterDcTunnelMash){
			CreateInterDcTunnelMash req = (CreateInterDcTunnelMash)m;
			createInterDcTunnelMash(req);
		}
	}
	
	//allocate a vm on a host server that has enough capacity.
	//then create a CreateVmRequest to create the vm on that host
	//server
	private void allocateVm(AllocateVmRequest originalRequest){
		VmInstance vmInstance = null;
		for(HostServer hostServer : this.hostServerList){
			vmInstance = hostServer.allocateVmInstance(originalRequest.getChainName(), originalRequest.getStageIndex());
			if(vmInstance == null){
				continue;
			}
			else{
				break;
			}
		}
		
		if(vmInstance!=null){
			logger.info("send CreateVmRequest to vmWorker");
			CreateVmRequest newRequest = new CreateVmRequest(this.getId(), vmInstance);
			Pending pending = new Pending(1, originalRequest);
			this.pendingMap.put(newRequest.getUUID(), pending);
			this.mh.sendTo("vmWorker", newRequest);
		}
		else{
			logger.info("can't allocate more vmInstance, reply to ServiceChainHandler");
			AllocateVmReply originalReply = new AllocateVmReply(this.getId(), null, originalRequest);
			this.mh.sendTo(originalReply.getAllocateVmRequest().getSourceId(), originalReply);
		}
	}
	
	//When VmWorker finishes creating the new vm, VmWorker will
	//return a CreateVmReply. Handle it by replying to the 
	//sender of the AllocateVmRequest a AllocateVmReply message
	private void handleCreateVmReply(CreateVmReply newReply){
		CreateVmRequest newRequest = newReply.getRequest();
		Pending pending = this.pendingMap.get(newRequest.getUUID());
		pending.addReply(newReply);
		
		if(newReply.getSuccessful()){
			//start to reply to AllocateVmRequest
			logger.info("got CreateVmReply from vmWorker");
			AllocateVmRequest originalRequest = (AllocateVmRequest)pending.getCachedMessage();
			AllocateVmReply originalReply = new AllocateVmReply(this.getId(), 
					                                newReply.getRequest().getVmInstance(), originalRequest);
			this.mh.sendTo(originalReply.getAllocateVmRequest().getSourceId(), originalReply);
		}
		this.pendingMap.remove(newRequest.getUUID());
	}
	
	//This is not that good. When the controller is initialized
	//VmAllocator will receive AddHostServerRequest. After receiving
	//This request, we will create bi-direction tunnels from the new host server
	//to all existing host servers.
	private void addHostServer(AddHostServerRequest request){
		if(this.hostServerList.size()==0){
			this.hostServerList.add(request.getHostServer());
		}
		else{
			try{
				HostServer serverToAdd = request.getHostServer();
				HostAgent newAgent = new HostAgent(serverToAdd.hostServerConfig);
				newAgent.connect();
				int returnVal = 0;
				for(int i=0; i<this.hostServerList.size(); i++){
					HostAgent oldAgent = new HostAgent(this.hostServerList.get(i).hostServerConfig);
					oldAgent.connect();
					
					returnVal=oldAgent.createTunnelTo(this.hostServerList.get(i), serverToAdd, this.vni);
					returnVal=newAgent.createTunnelTo(serverToAdd, this.hostServerList.get(i), this.vni);
					if(returnVal>0){
						this.vni = returnVal;
					}
					
					oldAgent.createRouteTo(this.hostServerList.get(i), serverToAdd);
					newAgent.createRouteTo(serverToAdd, this.hostServerList.get(i));
					
					oldAgent.disconnect();
				}
				newAgent.disconnect();
			}
			catch(Exception e){
				e.printStackTrace();
			}
			this.hostServerList.add(request.getHostServer());
		}
		HostServer hostServer = request.getHostServer();
		for(String chainName : hostServer.serviceChainDpidMap.keySet()){
			List<String> dpidList = hostServer.serviceChainDpidMap.get(chainName);
			for(int i=0; i<dpidList.size(); i++){
				this.dpidHostServerMap.put(DatapathId.of(dpidList.get(i)), hostServer);
				this.dpidStageIndexMap.put(DatapathId.of(dpidList.get(i)), new Integer(i));
			}
		}
	}
	
	private void createInterDcTunnelMash(CreateInterDcTunnelMash req){
		String srcIp = req.srcIp;
		Map<String, Integer> localcIndexMap = req.localcIndexMap;
		
		int basePort = this.hostServerList.get(0).tunnelPort;
		basePort += 1;
		
		int baseVni = this.vni;
		baseVni += 1;
		
		int srcIndex = localcIndexMap.get(srcIp).intValue();
		int dstIndex = 0;
		String dstIp = null;
		int interDcVniIndex = 0;
		
		try {
			Thread.sleep(10*1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for(int i=0; i<hostServerList.size(); i++){
			HostServer hostServer = hostServerList.get(i);
			//ServiceChainConfig chainConfig = hostServer.serviceChainConfigMap.get("DATA");
			HostAgent hostAgent = new HostAgent(hostServer.hostServerConfig);
			try{
				hostAgent.connect();
				//hostAgent.addFlowDstMac(chainConfig.bridges.get(0), hostServer.patchPort, hostServer.gatewayPort, hostServer.gatewayMac);
				for(String key : req.localcIndexMap.keySet()){
					int srcDcIndex = req.localcIndexMap.get(srcIp).intValue();
					int dstDcIndex = req.localcIndexMap.get(key).intValue();
					hostAgent.addStatFlow("stat-br", hostServer.statInPort, hostServer.statOutPort, srcDcIndex, dstDcIndex);
				}
				hostAgent.disconnect();
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		
		for(String key : localcIndexMap.keySet()){
			if(localcIndexMap.get(key).intValue() != srcIndex){
				dstIndex = localcIndexMap.get(key).intValue();
				dstIp = key;
				if(srcIndex<dstIndex){
					interDcVniIndex = req.globalBaseVni+srcIndex*localcIndexMap.size()+dstIndex;
				}
				else{
					interDcVniIndex = req.globalBaseVni+dstIndex*localcIndexMap.size()+srcIndex;
				}
				
				int newVniPort[] = createInterDcPort(new CreateInterDcTunnelRequest("this", srcIndex, srcIp, dstIndex, dstIp, 
						interDcVniIndex, basePort, baseVni));
				basePort = newVniPort[0];
				baseVni = newVniPort[1];
			}
		}
		
		mh.sendTo(req.sourceId, new CreateInterDcTunnelMashReply("vmAllocator"));
	}
	
	private int[] createInterDcPort(CreateInterDcTunnelRequest req){
		HostServer edgeServer = this.hostServerList.get(0);
		String edgeBridge  = edgeServer.serviceChainConfigMap.get("DATA").bridges.get(0);
		HostAgent edgeServerAgent = new HostAgent(edgeServer.hostServerConfig);
		
		try{
			edgeServerAgent.connect();
		} catch(Exception e){
			e.printStackTrace();
		}
		
		try {
			String portName = "s"+Integer.toString(req.srcDcIndex)+"d"+Integer.toString(req.dstDcIndex);
			edgeServerAgent.createTunnelPort(portName, edgeBridge, req.dstIp, req.tunnelPortNum, req.interDcVniIndex);
		} catch(Exception e){
			e.printStackTrace();
		}
		
		try {
			edgeServerAgent.addDpFlow(edgeBridge, req.tunnelPortNum, req.dstDcIndex, 0);
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
		int tunnelPortNum = req.tunnelPortNum+1;
		int baseVniIndex = req.baseVniIndex;
		
		edgeServer.dcIndexPortMap.put(new Integer(req.dstDcIndex), new Integer(req.tunnelPortNum));
		edgeServer.portDcIndexMap.put(new Integer(req.tunnelPortNum), new Integer(req.dstDcIndex));
		List<String> dpBridgeList = edgeServer.serviceChainConfigMap.get("DATA").bridges;
		
		ArrayList<Integer> patchPortList = new ArrayList<Integer>();
		for(int i=0; i<dpBridgeList.size(); i++){
			patchPortList.add(new Integer(0));
		}
		
		for(int i=1; i<dpBridgeList.size(); i++){
			String bridge = dpBridgeList.get(i);
			String localPortName = "wd"+Integer.toString(req.dstDcIndex)+"id"+Integer.toString(i);
			String remotePortName = "ed"+Integer.toString(req.dstDcIndex)+"id"+Integer.toString(i);
			
			try {
				edgeServerAgent.addPatchPort(bridge, localPortName, req.tunnelPortNum, remotePortName);
				edgeServerAgent.addPatchPort(edgeBridge, remotePortName, tunnelPortNum, localPortName);
			} catch(Exception e){
				e.printStackTrace();
			}
			
			try {
				edgeServerAgent.addDpFlow(bridge, req.tunnelPortNum, req.dstDcIndex, i);
				edgeServerAgent.addFlow(edgeBridge, tunnelPortNum, req.tunnelPortNum);
			} catch(Exception e){
				e.printStackTrace();
			}
			
			patchPortList.set(i, new Integer(tunnelPortNum));
			tunnelPortNum += 1;
		}
		
		String tailBridge = dpBridgeList.get(dpBridgeList.size()-1);
		try {
			edgeServerAgent.addTailFlow(tailBridge, edgeServer.patchPort);
		} catch  (Exception e1) {
			e1.printStackTrace();
		}
		
		edgeServer.dcIndexPatchPortListMap.put(new Integer(req.dstDcIndex), patchPortList);
		
		//ignore the following part
		for(int i=1; i<this.hostServerList.size(); i++){
			HostServer workingServer = this.hostServerList.get(i);
			HostAgent workingServerAgent = new HostAgent(workingServer.hostServerConfig);
			dpBridgeList = workingServer.serviceChainConfigMap.get("DATA").bridges;
			
			try {
				workingServerAgent.connect();
			} catch(Exception e){
				e.printStackTrace();
			}
			
			workingServer.dcIndexPortMap.put(new Integer(req.dstDcIndex), new Integer(req.tunnelPortNum));
			workingServer.portDcIndexMap.put(new Integer(req.tunnelPortNum), new Integer(req.dstDcIndex));
			
			for(int j=1; j<dpBridgeList.size()-1; j++){
				String workingBridge = dpBridgeList.get(j);
				
				try {
					String portName = "wd"+Integer.toString(req.dstDcIndex)+"vni"+Integer.toString(baseVniIndex);
					workingServerAgent.createTunnelPort(portName, workingBridge, edgeServer.hostServerConfig.internalIp, 
							req.tunnelPortNum, baseVniIndex);
					workingServerAgent.addDpFlow(workingBridge, req.tunnelPortNum, req.dstDcIndex, j);
				} catch(Exception e){
					e.printStackTrace();
				}
				
				try {
					String portName = "ed"+Integer.toString(req.dstDcIndex)+"vni"+Integer.toString(baseVniIndex);
					edgeServerAgent.createTunnelPort(portName, edgeBridge, workingServer.hostServerConfig.internalIp, 
							tunnelPortNum, baseVniIndex);
				} catch(Exception e){
					e.printStackTrace();
				}
				
				//Adding a static flow rule here
				
				try {
					edgeServerAgent.addFlow(edgeBridge, tunnelPortNum, req.tunnelPortNum);
				} catch(Exception e){
					e.printStackTrace();
				}
				tunnelPortNum += 1;
				baseVniIndex += 1;
			}
			
			tailBridge = dpBridgeList.get(dpBridgeList.size()-1);
			try {
				workingServerAgent.addTailFlow(tailBridge, workingServer.patchPort);
			} catch  (Exception e1) {
				e1.printStackTrace();
			}
			
			try {
				workingServerAgent.disconnect();
			} catch(Exception e){
				e.printStackTrace();
			}
		}
		
		try{
			edgeServerAgent.disconnect();
		} catch(Exception e){
			e.printStackTrace();
		}
		
		int returnVal[] = new int[2];
		returnVal[0] = tunnelPortNum;
		returnVal[1] = baseVniIndex;
		
		return returnVal;
	}
	
	//Deallocate an existing vm. Note that we first destroy the vm.
	//Then we deallocate the vm when the destroying process finishes.
	private void deallocateVm(DeallocateVmRequest originalRequest){
		DestroyVmRequest newRequest = new DestroyVmRequest(this.getId(), originalRequest.getVmInstance());
		Pending pending = new Pending(1, originalRequest);
		this.pendingMap.put(newRequest.getUUID(), pending);
		this.mh.sendTo("vmWorker", newRequest);
	}
	
	private void handleDestroyVmReply(DestroyVmReply newReply){
		DestroyVmRequest newRequest = newReply.getRequest();
		Pending pending = this.pendingMap.get(newRequest.getUUID());
		pending.addReply(newReply);
		
		if(newReply.getSuccessful()){
			newRequest.getVmInstance().hostServer.deallocateVmInstance(newRequest.getVmInstance());
		}
	}
}
