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
		if(m instanceof DNSUpdateReply){
			DNSUpdateReply reply = (DNSUpdateReply)m;
			handleDNSUpdateReply(reply);
		}
	}
	
	private void initServiceChain(InitServiceChainRequset originalRequest){
		NFVServiceChain serviceChain = originalRequest.getServiceChain();
		Pending pending = new Pending(serviceChain.serviceChainConfig.stages.size(), 
									  originalRequest);
		for(int i=0; i<serviceChain.serviceChainConfig.stages.size(); i++){
			AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
												serviceChain.serviceChainConfig.name,
												i, false);
			this.pendingMap.put(newRequest.getUUID(), pending);
			this.mh.sendTo("vmAllocator", newRequest);
		}
		
		for(int i=0; i<serviceChain.serviceChainConfig.stages.size(); i++){
			AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
												serviceChain.serviceChainConfig.name,
												i, true);
			this.pendingMap.put(newRequest.getUUID(), pending);
			this.mh.sendTo("vmAllocator", newRequest);
		}
	}
	
	private void allocateVm(AllocateVmRequest request){
		AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(), request.getChainName(),
											request.getStageIndex(), request.getIsBufferNode());
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
																"7776", "7775", vmInstance);
					this.mh.sendTo("subscriberConnector", request);
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
						"7776", "7775", vmInstance);
				this.mh.sendTo("subscriberConnector", request);
			}
		}
	}
	
	private void handleSubConnReply(SubConnReply reply){
		SubConnRequest request = reply.getSubConnRequest();
		VmInstance vmInstance = request.getVmInstance();
		
		if(vmInstance.serviceChainConfig.nVmInterface == 3){
			String serviceChainName = vmInstance.serviceChainConfig.name;
			this.serviceChainMap.get(serviceChainName).addNodeToChain(new NFVNode(vmInstance));
		
			String managementIp = request.getManagementIp();
			Socket subscriber1 = reply.getSubscriber1();
			if(!vmInstance.isBufferNode){
				this.serviceChainMap.get(serviceChainName).setScaleIndicator(vmInstance.stageIndex, false);
			}
			else{
				this.serviceChainMap.get(serviceChainName).setBufferScaleIndicator(vmInstance.stageIndex, false);
			}
			this.poller.register(new Pair<String, Socket>(managementIp+":1", subscriber1));
		}
		else{
			Socket subscriber1 = reply.getSubscriber1();
			Socket subscriber2 = reply.getSubscriber2();
			String domainName = "";
			if(vmInstance.stageIndex == 0){
				domainName = "bono.cw.t";
			}
			else {
				domainName = "sprout.cw.t";
			}
			DNSUpdateRequest dnsUpdateReq = new DNSUpdateRequest(this.getId(), domainName, 
																 vmInstance.operationIp, "add",
																 subscriber1, subscriber2, vmInstance);
			this.mh.sendTo("dnsUpdator", dnsUpdateReq);
		}
	}
	
	private void handleDNSUpdateReply(DNSUpdateReply reply){
		DNSUpdateRequest request = reply.getDNSUpdateReq();
		VmInstance vmInstance = request.getVmInstance();
		
		String serviceChainName = vmInstance.serviceChainConfig.name;
		this.serviceChainMap.get(serviceChainName).addNodeToChain(new NFVNode(vmInstance));
	
		String managementIp = vmInstance.managementIp;
		Socket subscriber1 = request.getSocket1();
		Socket subscriber2 = request.getSocket2();
		if(!vmInstance.isBufferNode){
			this.serviceChainMap.get(serviceChainName).setScaleIndicator(vmInstance.stageIndex, false);
		}
		else{
			this.serviceChainMap.get(serviceChainName).setBufferScaleIndicator(vmInstance.stageIndex, false);
		}
		this.poller.register(new Pair<String, Socket>(managementIp+":1", subscriber1));
		this.poller.register(new Pair<String, Socket>(managementIp+":2", subscriber2));
	}
	
	private void statUpdate(StatUpdateRequest request){
		ArrayList<String> statList = request.getStatList();
		String managementIp = request.getManagementIp();
		
		for(String chainName : this.serviceChainMap.keySet()){
			NFVServiceChain chain = this.serviceChainMap.get(chainName);
			synchronized(chain){
				if(chain.hasNode(managementIp)){
					NFVNode node = chain.getNode(managementIp);
					if(node.vmInstance.isBufferNode){
						chain.updateDataNodeStat(managementIp, statList);
						if(node.getState() == NFVNode.OVERLOAD){
							chain.setBufferScaleIndicator(node.vmInstance.stageIndex, true);
							AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
									                  node.vmInstance.serviceChainConfig.name,
									                              node.vmInstance.stageIndex, true);
							Pending pending = new Pending(1, null);
							this.pendingMap.put(newRequest.getUUID(), pending);
							this.mh.sendTo("vmAllocator", newRequest);
						}
						break;
					}
				}
				if(chain.hasNode(managementIp)&&(chain.serviceChainConfig.nVmInterface==3)){
					chain.updateDataNodeStat(managementIp, statList);
					
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
									System.out.println("!!! ScaleDown: Node "+ip+" is deleted from the chain");
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
											System.out.println("!!!Scale Down: Node "+ip+" is put into the scaleDownList");
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
								                              node.vmInstance.stageIndex, false);
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
				if(chain.hasNode(managementIp)&&(chain.serviceChainConfig.nVmInterface==2)){
					chain.updateControlNodeStat(managementIp, statList);
					NFVNode node = chain.getNode(managementIp);
					
					if(node.vmInstance.stageIndex == 0){
						Map<String, NFVNode> stageMap = chain.getStageMap(0);
						
						int nOverload = 0;
						for(String ip : stageMap.keySet()){
							NFVNode n = stageMap.get(ip);
							if(n.getTranState() == NFVNode.OVERLOAD){
								nOverload += 1;
							}
						}
						
						if(nOverload == stageMap.size()){
							//Let's find out which stage needs scaling.
							controlPlaneScaleUp(chain);
						}
					}
					
					break;
					
					/*Map<String, NFVNode> stageMap = chain.getStageMap(node.vmInstance.stageIndex);
					
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
					
					break;*/
				}
			}
		}
	}
	
	private void controlPlaneScaleUp(NFVServiceChain chain){
		
		int nBonoOverload = 0;
		Map<String, NFVNode> bonoMap = chain.getStageMap(0);
		for(String ip : bonoMap.keySet()){
			NFVNode n = bonoMap.get(ip);
			if(n.getState() == NFVNode.OVERLOAD){
				nBonoOverload += 1;
			}
		}
		
		int nSproutOverload = 0;
		Map<String, NFVNode> sproutMap = chain.getStageMap(1);
		for(String ip : sproutMap.keySet()){
			NFVNode n = sproutMap.get(ip);
			if(n.getState() == NFVNode.OVERLOAD){
				nSproutOverload += 1;
			}
		}
		
		if((nBonoOverload==bonoMap.size())&&(!chain.getScaleIndicator(0))){
			chain.setScaleIndicator(0, true);
			AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
	                  							chain.serviceChainConfig.name,
	                  							0, false);
			Pending pending = new Pending(1, null);
			this.pendingMap.put(newRequest.getUUID(), pending);
			this.mh.sendTo("vmAllocator", newRequest);
		}
		
		if((nSproutOverload==sproutMap.size())&&(!chain.getScaleIndicator(1))){
			chain.setScaleIndicator(1, true);
			AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
												chain.serviceChainConfig.name,
												1, false);
			Pending pending = new Pending(1, null);
			this.pendingMap.put(newRequest.getUUID(), pending);
			this.mh.sendTo("vmAllocator", newRequest);
		}
	}
}
