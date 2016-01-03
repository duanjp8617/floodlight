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
							serviceChain.addWorkingNode(bufferNode);
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
							serviceChain.addToBqRear(workingNode);
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
		NFVServiceChain dpServiceChain = serviceChainMap.get("DATA");
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
	
	//This handles the AllocateVmReply sent from VmAllocator.
	//We might probably want to change this function for 
	//global control.
	//When new vm is created, we need to connect to the 
	//stat reporter on that vm. This is done by creating a 
	//SubConnRequest.
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
				if(this.reactiveStart == true){
					//reactive scaling is enabled, we add the node to working node
					serviceChain.addToServiceChain(node);
					serviceChain.addWorkingNode(node);
				}
				else{
					serviceChain.addToServiceChain(node);
					serviceChain.addToBqRear(node);
				}
				serviceChain.setScaleIndicator(node.vmInstance.stageIndex, false);
			}
		}
	}
	
	//For data plane, once we have successfully finish connecting to the stat report
	//module on each vm, we will create a new NFVNode. And register
	//the poller with the new subscriber to poll the vm stat.
	//For control plane, when we finish connecting to the stat report module, 
	//we need to update the DNS.
	private void handleSubConnReply(SubConnReply reply){
		SubConnRequest request = reply.getSubConnRequest();
		VmInstance vmInstance = request.getVmInstance();
		AllocateVmRequest originalRequest = reply.getSubConnRequest().getOriginalRequest();
		
		if(vmInstance.serviceChainConfig.nVmInterface == 3){
			NFVServiceChain serviceChain = this.serviceChainMap.get(vmInstance.serviceChainConfig.name);
			NFVNode node = new NFVNode(vmInstance);
			addToServiceChain(serviceChain, node, originalRequest.getUUID());
		
			String managementIp = vmInstance.managementIp;
			Socket subscriber1 = reply.getSubscriber1();
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
					vmInstance.operationIp, "add",subscriber1, subscriber2, vmInstance, originalRequest);
			this.mh.sendTo("dnsUpdator", dnsUpdateReq);
		}
	}
	
	private void handleDNSUpdateReply(DNSUpdateReply reply){
		DNSUpdateRequest request = reply.getDNSUpdateReq();
		VmInstance vmInstance = request.getVmInstance();
		AllocateVmRequest originalRequest = request.getOriginalRequest();
		
		NFVServiceChain serviceChain = this.serviceChainMap.get(vmInstance.serviceChainConfig.name);
		NFVNode node = new NFVNode(vmInstance);
		addToServiceChain(serviceChain, node, originalRequest.getUUID());
	
		String managementIp = vmInstance.managementIp;
		Socket subscriber1 = request.getSocket1();
		Socket subscriber2 = request.getSocket2();
		this.poller.register(new Pair<String, Socket>(managementIp+":1", subscriber1));
		this.poller.register(new Pair<String, Socket>(managementIp+":2", subscriber2));
	}
	
	private void newProactiveScalingInterval(){
		NFVServiceChain dpServiceChain = serviceChainMap.get("DATA");
		dpServiceChain.addScalingInterval();
		
		NFVServiceChain cpServiceChain = serviceChainMap.get("CONTROL");
		cpServiceChain.addScalingInterval();
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
	
	//This is the driving function for reactive scaling.
	//Whenever the the stat poller polls a new stat 
	//, it will report the stat to ServiceChainHandler.
	//This function is where the stat is processed.
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
						//TODO:
						//Something needs to be done here....
						chain.scaleDownList.get(stageIndex).clear();
						chain.scaleDownCounter[stageIndex] = -1;
					}
					
					break;
				}
			}
		}
	}
}
