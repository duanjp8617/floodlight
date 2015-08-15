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
import java.util.List;
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
		if(m instanceof SubConnReply){
			SubConnReply reply = (SubConnReply) m;
			handleSubConnReply(reply);
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
				this.serviceChainMap.put(serviceChain.serviceChainConfig.name, serviceChain);
			
				ArrayList<Message> newReplyList = pending.getReplyList();
				for(int i=0; i<newReplyList.size(); i++){
					AllocateVmReply newReplz = (AllocateVmReply)newReplyList.get(i);
					VmInstance vmInstance = newReplz.getVmInstance();
					//serviceChain.addNodeToChain(new NFVNode(vmInstance));
					SubConnRequest request = new SubConnRequest(this.getId(),vmInstance.managementIp,
																"5555", vmInstance);
					this.mh.sendTo("subscriberConnector", request);
				}
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
				//this.serviceChainMap.get(serviceChainName).addNodeToChain(new NFVNode(vmInstance));
				SubConnRequest request = new SubConnRequest(this.getId(),vmInstance.managementIp,
						"5555", vmInstance);
				this.mh.sendTo("subscriberConnector", request);
			}
		}
	}
	
	private void handleSubConnReply(SubConnReply reply){
		SubConnRequest request = reply.getSubConnRequest();
		
		VmInstance vmInstance = request.getVmInstance();
		String serviceChainName = vmInstance.serviceChainConfig.name;
		this.serviceChainMap.get(serviceChainName).addNodeToChain(new NFVNode(vmInstance));
		
		String managementIp = request.getManagementIp();
		Socket subscriber = reply.getSubscriber();
		this.serviceChainMap.get(serviceChainName).setScaleIndicator(vmInstance.stageIndex, false);
		this.poller.register(new Pair<String, Socket>(managementIp, subscriber));
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
					int stageIndex = node.vmInstance.stageIndex;
					
					int nOverload = 0;
					for(String ip : stageMap.keySet()){
						NFVNode n = stageMap.get(ip);
						if(n.getState() == NFVNode.OVERLOAD){
							nOverload += 1;
						}
					}
					
					if(nOverload == 0){
						if(chain.scaleDownList.get(stageIndex).size()!=0){
							Map<String, Integer> stageScaleDownMap = 
									                chain.scaleDownList.get(stageIndex);
							List<String> deletedNodeIndexList = new ArrayList<String>();
							
							for(String ip : stageScaleDownMap.keySet()){
								NFVNode n = chain.getNode(ip);
								
								if(n.getActiveFlows() == 0){
									chain.deleteNodeFromChain(n);
									deletedNodeIndexList.add(ip);
									
									//Do some other thing.
									this.poller.unregister(n.getManagementIp());
									DeallocateVmRequest deallocationRequest = 
											new DeallocateVmRequest(this.getId(), n.vmInstance);
									this.mh.sendTo("vmAllocator", deallocationRequest);
								}
							}
							
							for(int i=0; i<deletedNodeIndexList.size(); i++){
								stageScaleDownMap.remove(deletedNodeIndexList.get(i));
							}
							
							if(stageScaleDownMap.size() == 0){
								chain.scaleDownCounter[stageIndex] = -1;
							}
						}
						else{
							if(chain.scaleDownCounter[stageIndex] == -1){
								chain.scaleDownCounter[stageIndex] = System.currentTimeMillis();
							}
							else{
								if((System.currentTimeMillis()-chain.scaleDownCounter[stageIndex])>=
									                                        15000){
									//The stage has been relatively idle for 15000s, put some 
									//nodes to the scaleDownList
									ArrayList<Pair<String, Integer>> flowNumList = 
											             chain.getFlowNumArray(stageIndex);
									
									int numToAdd = flowNumList.size()/2;
									for(int i=0; i<numToAdd; i++){
										int index = chain.getNodeWithLeastFlows(stageIndex, 
												             flowNumList);
										if(index < 0){
											continue;
										}
										else{
											String ip = flowNumList.get(index).first;
											chain.scaleDownList.get(stageIndex).put(ip, 
													                 new Integer(0));
											flowNumList.remove(index);
										}
									}
									
									chain.scaleDownCounter[stageIndex] = -1;
								}						
							}
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
						
						chain.scaleDownList.get(stageIndex).clear();
						chain.scaleDownCounter[stageIndex] = -1;
					}
					else if((nOverload > 0)&&(nOverload < stageMap.size())){
						chain.scaleDownList.get(stageIndex).clear();
						chain.scaleDownCounter[stageIndex] = -1;
					}
					
					break;
				}
			}
		}
	}
}
