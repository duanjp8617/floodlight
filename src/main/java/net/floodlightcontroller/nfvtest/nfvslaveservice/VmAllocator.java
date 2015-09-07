package net.floodlightcontroller.nfvtest.nfvslaveservice;
import java.util.concurrent.LinkedBlockingQueue;

import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.nfvutils.HostAgent;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer.*;
import net.floodlightcontroller.nfvtest.message.Pending;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.projectfloodlight.openflow.types.DatapathId;


public class VmAllocator extends MessageProcessor {
	private final ArrayList<HostServer> hostServerList;
	private final HashMap<UUID, Pending> pendingMap;
	private int vni;
	public final HashMap<DatapathId, HostServer> dpidHostServerMap;

	public VmAllocator(String id){
		this.id = id;
		this.queue = new LinkedBlockingQueue<Message>();
		this.hostServerList = new ArrayList<HostServer>();
		this.pendingMap = new HashMap<UUID, Pending>();
		this.vni=100;
		this.dpidHostServerMap = new HashMap<DatapathId, HostServer>();
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
	}
	
	private void allocateVm(AllocateVmRequest originalRequest){
		for(HostServer hostServer : this.hostServerList){
			VmInstance vmInstance = 
					hostServer.allocateVmInstance(originalRequest.getChainName(), originalRequest.getStageIndex(),
							                      originalRequest.getIsBufferNode());
			if(vmInstance == null){
				continue;
			}
			else{
				CreateVmRequest newRequest = new CreateVmRequest(this.getId(), vmInstance);
				Pending pending = new Pending(1, originalRequest);
				this.pendingMap.put(newRequest.getUUID(), pending);
				this.mh.sendTo("vmWorker", newRequest);
				break;
			}
		}
	}
	
	private void handleCreateVmReply(CreateVmReply newReply){
		CreateVmRequest newRequest = newReply.getRequest();
		Pending pending = this.pendingMap.get(newRequest.getUUID());
		pending.addReply(newReply);
		
		if(newReply.getSuccessful()){
			//start to reply to AllocateVmRequest
			AllocateVmRequest originalRequest = (AllocateVmRequest)pending.getCachedMessage();
			AllocateVmReply originalReply = new AllocateVmReply(this.getId(), 
					                                newReply.getRequest().getVmInstance(), originalRequest);
			System.out.println("Sending AllocateVmReply to: "+originalReply.getAllocateVmRequest().getSourceId());
			this.mh.sendTo(originalReply.getAllocateVmRequest().getSourceId(), originalReply);
		}
		this.pendingMap.remove(newRequest.getUUID());
	}
	
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
			}
		}
	}
	
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
			this.hostServerList.get(0).deallocateVmInstance(newRequest.getVmInstance());
		}
	}
}
