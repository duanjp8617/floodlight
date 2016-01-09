package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.concurrent.LinkedBlockingQueue;

import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.nfvtest.localcontroller.LocalController;
import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVNode;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVServiceChain;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer.VmInstance;
import net.floodlightcontroller.nfvtest.nfvutils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.zeromq.ZMQ.Socket;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;

public class ServiceChainHandler extends MessageProcessor { 
	private final HashMap<String, NFVServiceChain> serviceChainMap;
	private NFVZmqPoller poller;
	private final HashMap<UUID, Message> pendingMap;
	private Context context;
	
	private boolean reactiveStart;
	
	private int dcNum;
	private int dcIndex;
	
	protected IOFSwitchService switchService;
	
	private final HashMap<String, HostServer> hostServerMap;
	private final HashMap<DatapathId, HostServer> dpidHostServerMap;
	
	private final Logger logger =  LoggerFactory.getLogger(ServiceChainHandler.class);

	public ServiceChainHandler(String id, Context context, IOFSwitchService switchService){
		this.id = id;
		this.context = context;
		this.queue = new LinkedBlockingQueue<Message>();
		this.serviceChainMap = new HashMap<String, NFVServiceChain>();
		this.pendingMap = new HashMap<UUID, Message>();
		
		disableReactive();
		
		this.dcNum = 0;
		this.dcIndex = 0;
		
		this.switchService = switchService;
		
		this.hostServerMap = new HashMap<String, HostServer>();
		this.dpidHostServerMap = new HashMap<DatapathId, HostServer> ();
	}
	
	public void addHostServer(HostServer hostServer){
		this.hostServerMap.put(hostServer.hostServerConfig.managementIp, hostServer);
		for(String chainName : hostServer.serviceChainDpidMap.keySet()){
			List<String> dpidList = hostServer.serviceChainDpidMap.get(chainName);
			for(int i=0; i<dpidList.size(); i++){
				this.dpidHostServerMap.put(DatapathId.of(dpidList.get(i)), hostServer);
			}
		}
	}
	
	private void disableReactive(){
		this.reactiveStart = false;
	}
	
	private void enableReactive(){
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
		if(m instanceof LocalControllerNotification){
			LocalControllerNotification req = (LocalControllerNotification)m;
			handleLocalControllerNotification(req);
		}
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
		if(m instanceof NewProactiveIntervalRequest){
			newProactiveScalingInterval();
		}
		if(m instanceof ProactiveScalingStartRequest){
			proactiveScalingStart();
		}
		if(m instanceof CreateInterDcTunnelMash){
			CreateInterDcTunnelMash req = (CreateInterDcTunnelMash)m;
			relayCreateInterDcTunnelMash(req);
		}
		if(m instanceof CreateInterDcTunnelMashReply){
			handleCreateInterDcTunnelMashReply();
		}
	}
	
	private void handleLocalControllerNotification(LocalControllerNotification req){
		this.dcIndex = req.getDcIndex();
		this.dcNum = req.getDcNum();
	}
	
	private void relayCreateInterDcTunnelMash(CreateInterDcTunnelMash req){
		this.mh.sendTo("vmAllocator", req);
	}
	
	private void handleCreateInterDcTunnelMashReply(){
		Socket requester = context.socket(ZMQ.REQ);
		requester.connect("inproc://schSync");
		requester.send("TUNNELFINISH", 0);
		requester.recv(0);
		requester.close();
	}
	
	private void proactiveScalingStart(){
		disableReactive();
		
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
		
		logger.info("ServiceChainHandler receives proactive start request, stops reactive scaling");
	}
	
	private void handleProactiveProvision(NFVServiceChain serviceChain, int newProvision[]){
		synchronized(serviceChain){
			int oldProvision[] = serviceChain.getProvision();
			
			String print = serviceChain.serviceChainConfig.name + " service chain old configuration: ";
			for(int i=0; i<oldProvision.length; i++){
				print = print + " " + Integer.toString(oldProvision[i]); 
			}
			logger.info("{}", print);
			print = serviceChain.serviceChainConfig.name + " service chain new configuration: ";
			for(int i=0; i<oldProvision.length; i++){
				print = print + " " + Integer.toString(newProvision[i]); 
			}
			logger.info("{}", print);
			
			for(int i=0; i<oldProvision.length; i++){
				if(newProvision[i] > oldProvision[i]){
					//scale up
					int scaleUpNum = newProvision[i] - oldProvision[i];
					
					int j = 0;
					for(j=0; j<scaleUpNum; j++){
						NFVNode bufferNode = serviceChain.removeFromBqRear(i);
						if(bufferNode != null){
							
							logger.info(serviceChain.serviceChainConfig.name+" proactive scaling: transforming a stage "+
							Integer.toString(i)+" buffer node into working node");
							
							serviceChain.addWorkingNode(bufferNode);
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
										bufferNode.vmInstance.operationIp, "add");
								this.mh.sendTo("dnsUpdator", dnsUpdateReq);
							}
						}
						else{
							break;
						}
					}
					scaleUpNum = scaleUpNum - j;
					for(j=0; j<scaleUpNum; j++){
						
						logger.info(serviceChain.serviceChainConfig.name+" proactive scaling: creating a stage "+
								Integer.toString(i)+" working node");
						
						AllocateVmRequest newReq = new AllocateVmRequest(this.getId(),
								serviceChain.serviceChainConfig.name, i);
						this.pendingMap.put(newReq.getUUID(), newReq);
						//this.mh.sendTo("vmAllocator", newReq);
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
							
							logger.info(serviceChain.serviceChainConfig.name+" proactive scaling: transforming a stage "+
									Integer.toString(i)+" working node into buffer node");
							
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
										workingNode.vmInstance.operationIp, "delete");
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
			enableReactive();
			logger.info("service chain handler finishes executing proactive scaling decision");
		}
	}
	
	private void handleAllocateVmReply(AllocateVmReply reply){
		
		AllocateVmRequest originalRequest = reply.getAllocateVmRequest();
		VmInstance vmInstance = reply.getVmInstance();
		
		logger.info("receive AllocateVmReply for "+vmInstance.serviceChainConfig.name+" and stage "+
		Integer.toString(vmInstance.stageIndex));
		
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
				
				if(serviceChain.serviceChainConfig.nVmInterface == 2){
					String domainName = "";
					if(node.vmInstance.stageIndex == 0){
						domainName = "bono.cw.t";
					}
					else {
						domainName = "sprout.cw.t";
					}
					DNSUpdateRequest dnsUpdateReq = new DNSUpdateRequest(this.getId(), domainName, 
							node.vmInstance.operationIp, "add");
					this.mh.sendTo("dnsUpdator", dnsUpdateReq);
				}
				
				pendingMap.remove(uuid);
				if(pendingMap.size() == 0){
					Socket requester = context.socket(ZMQ.REQ);
					requester.connect("inproc://schSync");
					requester.send("COMPLETE", 0);
					requester.recv(0);
					requester.close();
					enableReactive();
					logger.info("service chain handler finishes executing proactive scaling decision");
				}
			}
			else{
				//a reactive scaling request is finished
				serviceChain.addToServiceChain(node);
				if(this.reactiveStart == true){
					//reactive scaling is enabled, we add the node to working node
					serviceChain.addWorkingNode(node);
					
					if(serviceChain.serviceChainConfig.nVmInterface == 2){
						String domainName = "";
						if(node.vmInstance.stageIndex == 0){
							domainName = "bono.cw.t";
						}
						else {
							domainName = "sprout.cw.t";
						}
						DNSUpdateRequest dnsUpdateReq = new DNSUpdateRequest(this.getId(), domainName, 
								node.vmInstance.operationIp, "add");
						this.mh.sendTo("dnsUpdator", dnsUpdateReq);
					}
				}
				else{
					serviceChain.addToBqRear(node);
				}
				serviceChain.setScaleIndicator(node.vmInstance.stageIndex, false);
			}
		}
	}
	
	private void handleSubConnReply(SubConnReply reply){
		SubConnRequest request = reply.getSubConnRequest();
		VmInstance vmInstance = request.getVmInstance();
		Message originalMessage = reply.getSubConnRequest().getOriginalMessage();
		
		NFVServiceChain serviceChain = this.serviceChainMap.get(vmInstance.serviceChainConfig.name);
		NFVNode node = new NFVNode(vmInstance);
		
		logger.info("receive SubConnReply for "+vmInstance.serviceChainConfig.name+" and stage "+
		Integer.toString(vmInstance.stageIndex));
		
		if(vmInstance.serviceChainConfig.nVmInterface == 3){
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
		}
		
		addToServiceChain(serviceChain, node, originalMessage.getUUID());
	}
	
	private void newProactiveScalingInterval(){
		NFVServiceChain dpServiceChain = serviceChainMap.get("DATA");
		dpServiceChain.addScalingInterval();
		
		NFVServiceChain cpServiceChain = serviceChainMap.get("CONTROL");
		cpServiceChain.addScalingInterval();
		
		NFVNode destroyNode = null;
		synchronized(dpServiceChain){
			for(int i=0; i<dpServiceChain.serviceChainConfig.stages.size(); i++){
				destroyNode = dpServiceChain.removeFromBqHead(i);
				while(destroyNode != null){
					logger.info("a DATA node will be destroyed");
					dpServiceChain.addDestroyNode(destroyNode);
					destroyNode = dpServiceChain.removeFromBqHead(i);
				}
			}
		}
		
		synchronized(cpServiceChain){
			for(int i=0; i<cpServiceChain.serviceChainConfig.stages.size(); i++){
				destroyNode = cpServiceChain.removeFromBqHead(i);
				while(destroyNode != null){
					logger.info("a CONTROL node will be destroyed");
					cpServiceChain.addDestroyNode(destroyNode);
					destroyNode = cpServiceChain.removeFromBqHead(i);
				}
			}
		}
	}
	
	private void statUpdate(StatUpdateRequest request){
		ArrayList<String> statList = request.getStatList();
		String managementIp = request.getManagementIp();
		
		String print = "receive node stat report for node "+managementIp+": ";
		for(int i=0; i<statList.size(); i++){
			print = print+" "+statList.get(i);
		}
		logger.info("{}", print);
		
		for(String chainName : this.serviceChainMap.keySet()){
			NFVServiceChain chain = this.serviceChainMap.get(chainName);
			synchronized(chain){
				if(chain.hasNode(managementIp)){
					//The stat comes from a NFV node on this service chain.
					//update the stat on this node.
					if(chain.serviceChainConfig.nVmInterface == 3){
						chain.updateDataNodeStat(managementIp, statList);
					}
					else{
						chain.updateControlNodeStat(managementIp, statList);
					}
					
					if(reactiveStart == true){
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
							//Here we trigger a reactive scaling condition.
							//Create a new vm.
							NFVNode bufferNode = chain.removeFromBqRear(node.vmInstance.stageIndex);
							if(bufferNode != null){
								chain.addWorkingNode(bufferNode);
								
								if(chain.serviceChainConfig.nVmInterface == 2){
									String domainName = "";
									if(bufferNode.vmInstance.stageIndex == 0){
										domainName = "bono.cw.t";
									}
									else {
										domainName = "sprout.cw.t";
									}
									
									DNSUpdateRequest dnsUpdateReq = new DNSUpdateRequest(this.getId(), domainName, 
											bufferNode.vmInstance.operationIp, "add");
									this.mh.sendTo("dnsUpdator", dnsUpdateReq);
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
							this.poller.unregister(destroyNode.getManagementIp()+":1");
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
