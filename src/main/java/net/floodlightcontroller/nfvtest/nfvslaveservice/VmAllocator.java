package net.floodlightcontroller.nfvtest.nfvslaveservice;
import java.util.concurrent.LinkedBlockingQueue;

import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer.*;
import net.floodlightcontroller.nfvtest.message.Pending;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.zeromq.ZMQ.Socket;


public class VmAllocator extends MessageProcessor {
	private final ArrayList<HostServer> hostServerList;
	private final HashMap<UUID, Pending> pendingMap;
	
	public VmAllocator(String id){
		this.id = id;
		this.queue = new LinkedBlockingQueue<Message>();
		this.hostServerList = new ArrayList<HostServer>();
		this.pendingMap = new HashMap<UUID, Pending>();
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
		else if(m instanceof SubConnReply){
			SubConnReply reply = (SubConnReply)m;
			handleSubConnReply(reply);
		}
	}
	
	private void allocateVm(AllocateVmRequest originalRequest){
		for(HostServer hostServer : this.hostServerList){
			VmInstance vmInstance = 
					hostServer.allocateVmInstance(originalRequest.getChainName(), originalRequest.getStageIndex());
			if(vmInstance == null){
				continue;
			}
			else{
				CreateVmRequest newRequest = new CreateVmRequest(this.getId(), vmInstance);
				Pending pending = new Pending(1, originalRequest);
				this.pendingMap.put(newRequest.getUUID(), pending);
				this.mh.sendTo("vmWorker", newRequest);
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
			VmInstance vmInstance = newReply.getRequest().getVmInstance();
			
			SubConnRequest request = new SubConnRequest(this.getId(),vmInstance.managementIp,
														"5555", originalRequest, vmInstance);
			this.mh.sendTo("subscriberConnector", request);
		}
		this.pendingMap.remove(newRequest.getUUID());
	}
	
	private void addHostServer(AddHostServerRequest request){
		this.hostServerList.add(request.getHostServer());
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
	
	private void handleSubConnReply(SubConnReply reply){
		SubConnRequest request = reply.getSubConnRequest();
		
		Socket subscriber = reply.getSubscriber();
		VmInstance vmInstance = request.getVmInstance();
		AllocateVmRequest originalRequest = request.getAllocateVmRequest();
		
		AllocateVmReply originalReply = new AllocateVmReply(this.getId(),
										 vmInstance, originalRequest, subscriber);
		this.mh.sendTo(originalRequest.getSourceId(), originalReply);
	}
}
