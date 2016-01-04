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
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;

public class ServiceChainHandler extends MessageProcessor {
	
	private final HashMap<String, NFVServiceChain> serviceChainMap;
	private NFVZmqPoller poller;
	private final HashMap<UUID, Message> pendingMap;
	private Context context;
	
	private boolean reactiveStart;

	public ServiceChainHandler(String id, Context context){
		this.id = id;
		this.context = context;
		this.queue = new LinkedBlockingQueue<Message>();
		this.serviceChainMap = new HashMap<String, NFVServiceChain>();
		this.pendingMap = new HashMap<UUID, Message>();
		
		this.reactiveStart = false;
	}
	
	public void startPollerThread(){
		poller = new NFVZmqPoller(this.mh);
		Thread pollerThread = new Thread(this.poller);
		pollerThread.start();
	}
	
	public void addServiceChain(NFVServiceChain serviceChain){
		this.serviceChainMap.put(serviceChain.serviceChainConfig.name, serviceChain);
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
		if(m instanceof ProactiveScalingRequest){
			ProactiveScalingRequest request = (ProactiveScalingRequest)m;
			handleProactiveScalingRequest(request);
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
		if(m instanceof NewProactiveIntervalRequest){
			newProactiveScalingInterval();
		}
		if(m instanceof ProactiveScalingStartRequest){
			proactiveScalingStart();
		}
	}
	
	private void proactiveScalingStart(){
		this.reactiveStart = false;
		
		NFVServiceChain dpServiceChain = serviceChainMap.get("DATA");
		int dpProvision[] = dpServiceChain.getProvision();
		
		NFVServiceChain cpServiceChain = serviceChainMap.get("CONTROL");
		int cpProvision[] = cpServiceChain.getProvision();
		
		Socket requester = context.socket(ZMQ.REQ);
		requester.connect("inproc://schSync");
		requester.send("PROVISION", ZMQ.SNDMORE);
		
		String dpProvisionStr = "";
		for(int i=0; i<dpProvision.length; i++){
			dpProvisionStr  = dpProvisionStr + Integer.toString(dpProvision[i]) + " ";
		}
		requester.send(dpProvisionStr, ZMQ.SNDMORE);
		
		String cpProvisionStr = "";
		for(int i=0; i<cpProvision.length; i++){
			cpProvisionStr  = cpProvisionStr + Integer.toString(cpProvision[i]) + " ";
		}
		requester.send(cpProvisionStr, 0);
		
		requester.recv(0);
		requester.close();
	}
	
	private void handleProactiveProvision(NFVServiceChain serviceChain, int newProvision[]){
		synchronized(serviceChain){
			int oldProvision[] = serviceChain.getProvision();
			
			for(int i=0; i<oldProvision.length; i++){
				if(newProvision[i] > oldProvision[i]){
					//scale up
					int scaleUpNum = newProvision[i] - oldProvision[i];
					
					int j = 0;
					for(j=0; j<scaleUpNum; j++){
						NFVNode bufferNode = serviceChain.removeFromBqRear();
						if(bufferNode != null){
							if(serviceChain.serviceChainConfig.nVmInterface == 2){
								//in this case, we need to add the IP address of the control plane
								//middlebox to the DNS.
								String domainName = "";
								if(bufferNode.vmInstance.stageIndex == 0){
									domainName = "bono.cw.t";
								}
								else {
									domainName = "sprout.cw.t";
								}
								
								DNSUpdateRequest dnsUpdateReq = new DNSUpdateRequest(this.getId(), domainName, 
										bufferNode.vmInstance.operationIp, "add", bufferNode, null);
								this.pendingMap.put(dnsUpdateReq.getUUID(), dnsUpdateReq);
								this.mh.sendTo("dnsUpdator", dnsUpdateReq);
							}
							else{
								serviceChain.addWorkingNode(bufferNode);
							}
						}
						else{
							break;
						}
					}
					scaleUpNum = scaleUpNum - j;
					for(j=0; j<scaleUpNum; j++){
						AllocateVmRequest newReq = new AllocateVmRequest(this.getId(),
								serviceChain.serviceChainConfig.name, i);
						this.pendingMap.put(newReq.getUUID(), newReq);
						this.mh.sendTo("vmAllocator", newReq);
					}
				}
				else if(newProvision[i] < oldProvision[i]){
					//scaleDown
					int scaleDownNum = oldProvision[i]-newProvision[i];
					for(int j=0; j<scaleDownNum; j++){
						NFVNode workingNode = serviceChain.getNormalWorkingNode(i);
						if(workingNode == null){
							break;
						}
						else{
							serviceChain.removeWorkingNode(workingNode);
							serviceChain.addToBqRear(workingNode);
							if(serviceChain.serviceChainConfig.nVmInterface == 2){
								String domainName = "";
								if(workingNode.vmInstance.stageIndex == 0){
									domainName = "bono.cw.t";
								}
								else {
									domainName = "sprout.cw.t";
								}
								
								DNSUpdateRequest dnsUpdateReq = new DNSUpdateRequest(this.getId(), domainName, 
										workingNode.vmInstance.operationIp, "delete", workingNode, null);
								this.pendingMap.put(dnsUpdateReq.getUUID(), dnsUpdateReq);
								this.mh.sendTo("dnsUpdator", dnsUpdateReq);
							}
						}
					}
				}
				else{
					//do nothing
				}
			}
		}
	}
	
	private void handleProactiveScalingRequest(ProactiveScalingRequest req){
		pendingMap.clear();
		
		int newDpProvision[] = req.getLocalDpProvision();
		int newDpPaths[][][] = req.getDpPaths();
		NFVServiceChain dpServiceChain = serviceChainMap.get("DATA");
		dpServiceChain.addNextDpPaths(newDpPaths);
		handleProactiveProvision(dpServiceChain, newDpProvision);
		
		int newCpProvision[] = req.getLocalCpProvision();
		NFVServiceChain cpServiceChain = serviceChainMap.get("CONTROL");
		handleProactiveProvision(cpServiceChain, newCpProvision);
		
		if(pendingMap.size() == 0){
			Socket requester = context.socket(ZMQ.REQ);
			requester.connect("inproc://schSync");
			requester.send("COMPLETE", 0);
			requester.recv(0);
			requester.close();
			this.reactiveStart = true;
		}
	}
	
	private void handleAllocateVmReply(AllocateVmReply reply){
		AllocateVmRequest originalRequest = reply.getAllocateVmRequest();
		VmInstance vmInstance = reply.getVmInstance();
		
		String serviceChainName = vmInstance.serviceChainConfig.name;
		if(this.serviceChainMap.containsKey(serviceChainName)){
			SubConnRequest request = new SubConnRequest(this.getId(),vmInstance.managementIp,
					"7776", "7775", vmInstance, originalRequest);
			this.mh.sendTo("subscriberConnector", request);
		}
	}
	
	private void addToServiceChain(NFVServiceChain serviceChain, NFVNode node, UUID uuid){
		synchronized(serviceChain){
			if(pendingMap.containsKey(uuid)){
				//one of the proactive scaling requests is finished.
				serviceChain.addToServiceChain(node);
				serviceChain.addWorkingNode(node);
				pendingMap.remove(uuid);
				if(pendingMap.size() == 0){
					Socket requester = context.socket(ZMQ.REQ);
					requester.connect("inproc://schSync");
					requester.send("COMPLETE", 0);
					requester.recv(0);
					requester.close();
					this.reactiveStart = true;
				}
			}
			else{
				//a reactive scaling request is finished
				serviceChain.addToServiceChain(node);
				if(this.reactiveStart == true){
					//reactive scaling is enabled, we add the node to working node
					serviceChain.addWorkingNode(node);
				}
				else{
					serviceChain.addToBqRear(node);
					if(serviceChain.serviceChainConfig.nVmInterface == 2){
						String domainName = "";
						if(node.vmInstance.stageIndex == 0){
							domainName = "bono.cw.t";
						}
						else {
							domainName = "sprout.cw.t";
						}
						
						DNSUpdateRequest dnsUpdateReq = new DNSUpdateRequest(this.getId(), domainName, 
								node.vmInstance.operationIp, "delete", node, null);
						this.mh.sendTo("dnsUpdator", dnsUpdateReq);
					}
				}
				serviceChain.setScaleIndicator(node.vmInstance.stageIndex, false);
			}
		}
	}
	
	private void handleSubConnReply(SubConnReply reply){
		SubConnRequest request = reply.getSubConnRequest();
		VmInstance vmInstance = request.getVmInstance();
		Message originalMessage = reply.getSubConnRequest().getOriginalMessage();
		
		if(vmInstance.serviceChainConfig.nVmInterface == 3){
			NFVServiceChain serviceChain = this.serviceChainMap.get(vmInstance.serviceChainConfig.name);
			NFVNode node = new NFVNode(vmInstance);
			addToServiceChain(serviceChain, node, originalMessage.getUUID());
		
			String managementIp = vmInstance.managementIp;
			Socket subscriber1 = reply.getSubscriber1();
			this.poller.register(new Pair<String, Socket>(managementIp+":1", subscriber1));
		}
		else{
			String managementIp = vmInstance.managementIp;
			Socket subscriber1 = reply.getSubscriber1();
			Socket subscriber2 = reply.getSubscriber2();
			this.poller.register(new Pair<String, Socket>(managementIp+":1", subscriber1));
			this.poller.register(new Pair<String, Socket>(managementIp+":2", subscriber2));
			
			String domainName = "";
			if(vmInstance.stageIndex == 0){
				domainName = "bono.cw.t";
			}
			else {
				domainName = "sprout.cw.t";
			}
			NFVNode node = new NFVNode(vmInstance);
			DNSUpdateRequest dnsUpdateReq = new DNSUpdateRequest(this.getId(), domainName, 
					vmInstance.operationIp, "add", node, originalMessage);
			this.mh.sendTo("dnsUpdator", dnsUpdateReq);
		}
	}
	
	private void handleDNSUpdateReply(DNSUpdateReply reply){
		DNSUpdateRequest request = reply.getDNSUpdateReq();
		NFVNode node = request.getNode();
		Message originalMessage = request.getOriginalMessage();
		
		NFVServiceChain serviceChain = this.serviceChainMap.get(node.vmInstance.serviceChainConfig.name);
		if(request.getAddOrDelete().equals("add")){
			addToServiceChain(serviceChain, node, originalMessage.getUUID());
		}
		else{
			if(pendingMap.containsKey(originalMessage.getUUID())){
				pendingMap.remove(originalMessage.getUUID());
				if(pendingMap.size() == 0){
					Socket requester = context.socket(ZMQ.REQ);
					requester.connect("inproc://schSync");
					requester.send("COMPLETE", 0);
					requester.recv(0);
					requester.close();
					this.reactiveStart = true;
				}
			}
		}
	}
	
	private void newProactiveScalingInterval(){
		NFVServiceChain dpServiceChain = serviceChainMap.get("DATA");
		dpServiceChain.addScalingInterval();
		
		NFVServiceChain cpServiceChain = serviceChainMap.get("CONTROL");
		cpServiceChain.addScalingInterval();
		
		NFVNode destroyNode = null;
		synchronized(dpServiceChain){
			destroyNode = dpServiceChain.removeFromBqHead();
			while(destroyNode != null){
				dpServiceChain.addDestroyNode(destroyNode);
			}
		}
		
		synchronized(cpServiceChain){
			destroyNode = cpServiceChain.removeFromBqHead();
			while(destroyNode != null){
				cpServiceChain.addDestroyNode(destroyNode);
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
					//The stat comes from a NFV node on this service chain.
					//update the stat on this node.
					chain.updateDataNodeStat(managementIp, statList);
					
					if(reactiveStart == true){
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
						
						if( (nOverload == stageMap.size())&&
						    (!chain.getScaleIndicator(node.vmInstance.stageIndex)) ){
							//Here we trigger a reactive scaling condition.
							//Create a new vm.
							NFVNode bufferNode = chain.removeFromBqRear();
							if(bufferNode != null){
								if(chain.serviceChainConfig.nVmInterface == 2){
									String domainName = "";
									if(bufferNode.vmInstance.stageIndex == 0){
										domainName = "bono.cw.t";
									}
									else {
										domainName = "sprout.cw.t";
									}
									
									DNSUpdateRequest dnsUpdateReq = new DNSUpdateRequest(this.getId(), domainName, 
											bufferNode.vmInstance.operationIp, "add", bufferNode, null);
									this.mh.sendTo("dnsUpdator", dnsUpdateReq);
								}
								else{
									chain.addWorkingNode(bufferNode);
								}
							}
							else{
								chain.setScaleIndicator(node.vmInstance.stageIndex, true);
								AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
										                  node.vmInstance.serviceChainConfig.name,
										                              node.vmInstance.stageIndex);
								this.mh.sendTo("vmAllocator", newRequest);
							}
						}
					}
					
					ArrayList<String> list = new ArrayList<String>();
					for(String key : chain.destroyNodeMap.keySet()){
						NFVNode destroyNode = chain.destroyNodeMap.get(key);
						chain.removeFromServiceChain(destroyNode);
						if(chain.serviceChainConfig.nVmInterface == 3){
							this.poller.unregister(destroyNode.getManagementIp());
						}
						else{
							this.poller.unregister(destroyNode.getManagementIp()+":1");
							this.poller.unregister(destroyNode.getManagementIp()+":2");
						}
						DeallocateVmRequest deallocationRequest = 
								new DeallocateVmRequest(this.getId(), destroyNode.vmInstance);
						this.mh.sendTo("vmAllocator", deallocationRequest);
						list.add(key);
					}
					for(int i=0; i<list.size(); i++){
						chain.destroyNodeMap.remove(list.get(i));
					}
					
					break;
				}
			}
		}
	}
}
