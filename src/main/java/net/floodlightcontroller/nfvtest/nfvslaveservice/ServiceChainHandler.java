package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.concurrent.LinkedBlockingQueue;

import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.message.Pending;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVNode;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVServiceChain;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer.VmInstance;
import net.floodlightcontroller.nfvtest.nfvutils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.zeromq.ZMQ.Socket;

public class ServiceChainHandler extends MessageProcessor {
	private final HashMap<String, NFVServiceChain> serviceChainMap;
	private final HashMap<UUID, Pending> pendingMap;
	private NFVZmqPoller poller;
	

	public ServiceChainHandler(String id){
		this.id = id;
		this.queue = new LinkedBlockingQueue<Message>();
		serviceChainMap = new HashMap<String, NFVServiceChain>();
		pendingMap = new HashMap<UUID, Pending>();
	}
	
	public void startPollerThread(){
		poller = new NFVZmqPoller(this.mh);
		Thread pollerThread = new Thread(this.poller);
		pollerThread.start();
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub
		if(m instanceof InitServiceChainRequset){
			InitServiceChainRequset request = (InitServiceChainRequset)m;
			initServiceChain(request);
		}
		if(m instanceof AllocateVmRequest){
			AllocateVmRequest request = (AllocateVmRequest)m;
			allocateVm(request);
		}
		if(m instanceof AllocateVmReply){
			AllocateVmReply reply = (AllocateVmReply)m;
			handleAllocateVmReply(reply);
		}
		if(m instanceof StatUpdateRequest){
			StatUpdateRequest request = (StatUpdateRequest)m;
			statUpdate(request);
		}
	}
	
	private void initServiceChain(InitServiceChainRequset originalRequest){
		NFVServiceChain serviceChain = originalRequest.getServiceChain();
		Pending pending = new Pending(serviceChain.serviceChainConfig.stages.size(), 
									  originalRequest);
		for(int i=0; i<serviceChain.serviceChainConfig.stages.size(); i++){
			AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
												serviceChain.serviceChainConfig.name,
												i);
			this.pendingMap.put(newRequest.getUUID(), pending);
			this.mh.sendTo("vmAllocator", newRequest);
		}
	}
	
	private void allocateVm(AllocateVmRequest request){
		AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(), request.getChainName(),
											request.getStageIndex());
		Pending pending = new Pending(1, null);
		this.pendingMap.put(newRequest.getUUID(), pending);
		this.mh.sendTo("vmAllocator", newRequest);
	}
	
	private void handleAllocateVmReply(AllocateVmReply newReply){
		AllocateVmRequest newRequest = newReply.getAllocateVmRequest();
		Pending pending = this.pendingMap.get(newRequest.getUUID());
		this.pendingMap.remove(newRequest.getUUID());
		
		if(pending.getCachedMessage()!=null){
			if(pending.addReply(newReply)){
				InitServiceChainRequset originalRequest = 
						(InitServiceChainRequset)pending.getCachedMessage();
				NFVServiceChain serviceChain = originalRequest.getServiceChain();
			
				ArrayList<Message> newReplyList = pending.getReplyList();
				for(int i=0; i<newReplyList.size(); i++){
					AllocateVmReply newReplz = (AllocateVmReply)newReplyList.get(i);
					VmInstance vmInstance = newReplz.getVmInstance();
					serviceChain.addNodeToChain(new NFVNode(vmInstance));
					this.poller.register(new Pair<String, Socket>(vmInstance.managementIp, newReply.getSubscriber()));
				}
				this.serviceChainMap.put(serviceChain.serviceChainConfig.name, serviceChain);
				synchronized(serviceChain){
					serviceChain.notify();
				}
			}
		}
		else{
			pending.addReply(newReply);
			VmInstance vmInstance = newReply.getVmInstance();
			String serviceChainName = vmInstance.serviceChainConfig.name;
			if(this.serviceChainMap.containsKey(serviceChainName)){
				this.serviceChainMap.get(serviceChainName).addNodeToChain(new NFVNode(vmInstance));
				this.poller.register(new Pair<String, Socket>(vmInstance.managementIp, newReply.getSubscriber()));
				this.serviceChainMap.get(serviceChainName).setScaleIndicator(vmInstance.stageIndex, false);
				synchronized(this.serviceChainMap.get(serviceChainName)){
					this.serviceChainMap.get(serviceChainName).notify();
				}
			}
		}
	}
	
	private void statUpdate(StatUpdateRequest request){
		ArrayList<String> statList = request.getStatList();
		String managementIp = request.getManagementIp();
		
		for(String chainName : this.serviceChainMap.keySet()){
			NFVServiceChain chain = this.serviceChainMap.get(chainName);
			synchronized(chain){
				if(chain.hasNode(managementIp)){
					chain.updateNodeStat(managementIp, statList);
							
					NFVNode node = chain.getNode(managementIp);
					Map<String, NFVNode> stageMap = chain.getStageMap(node.vmInstance.stageIndex);
				
					int nOverload = 0;
					for(String ip : stageMap.keySet()){
						NFVNode n = stageMap.get(ip);
						if(n.getState() == NFVNode.OVERLOAD){
							nOverload += 1;
						}
					}
					if( (nOverload == stageMap.size())&&
						(!chain.getScaleIndicator(node.vmInstance.stageIndex)) ){
						chain.setScaleIndicator(node.vmInstance.stageIndex, true);
						AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(), 
												  node.vmInstance.serviceChainConfig.name,
												  	           node.vmInstance.stageIndex);
						Pending pending = new Pending(1, null);
						this.pendingMap.put(newRequest.getUUID(), pending);
						this.mh.sendTo("vmAllocator", newRequest);
					}
					break;
				}
			}
		}
	}
	
}
