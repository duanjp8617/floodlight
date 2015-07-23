package net.floodlightcontroller.nfvtest.nfvslaveservice;
import java.util.concurrent.LinkedBlockingQueue;

import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.KillSelfRequest;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer.*;
import net.floodlightcontroller.nfvtest.message.Pending;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;


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
			AllocateVmReply originalReply = new AllocateVmReply(this.getId(), 
					                                newReply.getRequest().getVmInstance(), originalRequest);
			System.out.println("Sending AllocateVmReply to: "+originalReply.getAllocateVmRequest().getSourceId());
			this.mh.sendTo(originalReply.getAllocateVmRequest().getSourceId(), originalReply);
		}
		this.pendingMap.remove(newRequest.getUUID());
	}
	
	private void addHostServer(AddHostServerRequest request){
		this.hostServerList.add(request.getHostServer());
	}
}
