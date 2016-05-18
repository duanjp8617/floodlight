package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.concurrent.LinkedBlockingQueue;

import net.floodlightcontroller.core.IOFSwitch;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.zeromq.ZMQ.Socket;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpEcn;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;

public class ServiceChainHandler extends MessageProcessor { 
	private final HashMap<String, NFVServiceChain> serviceChainMap;
	private NFVZmqPoller poller;
	private final HashMap<UUID, Message> pendingMap;
	private final HashMap<UUID, Message> reactiveMap;
	private final HashMap<UUID, Message> errorMap;
	private final HashMap<String, Integer> errorIpMap;
	private int[] dpReactiveCounter;
	private int[] cpReactiveCounter;
	private Context context;
	private Socket requester;
	
	private boolean reactiveStart;
	private boolean enableReactive;
	
	private int dcNum;
	private int dcIndex;
	
	protected IOFSwitchService switchService;
	
	private final ArrayList<HostServer> hostServerList;
	private final HashMap<DatapathId, HostServer> dpidHostServerMap;
	public final HashMap<DatapathId, Integer> dpidStageIndexMap;
	
	private List<Map<Integer, Integer>> indexMap = null;
	
	private final Logger logger =  LoggerFactory.getLogger(ServiceChainHandler.class);

	public ServiceChainHandler(String id, Context context, IOFSwitchService switchService, boolean enableReactive){
		this.id = id;
		this.context = context;
		this.queue = new LinkedBlockingQueue<Message>();
		this.serviceChainMap = new HashMap<String, NFVServiceChain>();
		this.pendingMap = new HashMap<UUID, Message>();
		this.errorMap = new HashMap<UUID, Message>();
		this.errorIpMap = new HashMap<String, Integer>();
		this.reactiveMap = new HashMap<UUID, Message>();
		
		disableReactive();
		
		this.dcNum = 0;
		this.dcIndex = 0;
		
		this.switchService = switchService;
		
		this.hostServerList = new ArrayList<HostServer>();
		this.dpidHostServerMap = new HashMap<DatapathId, HostServer> ();
		this.dpidStageIndexMap = new HashMap<DatapathId, Integer>();
		
		this.enableReactive = enableReactive;
	}
	
	public void addHostServer(HostServer hostServer){
		this.hostServerList.add(hostServer);
		for(String chainName : hostServer.serviceChainDpidMap.keySet()){
			List<String> dpidList = hostServer.serviceChainDpidMap.get(chainName);
			for(int i=0; i<dpidList.size(); i++){
				this.dpidHostServerMap.put(DatapathId.of(dpidList.get(i)), hostServer);
				this.dpidStageIndexMap.put(DatapathId.of(dpidList.get(i)), i);
			}
		}
	}
	
	private void disableReactive(){
		this.reactiveStart = false;
	}
	
	private void enableReactive(){
		this.reactiveStart = enableReactive;
	}
	
	public void startPollerThread(){
		poller = new NFVZmqPoller(this.mh);
		Thread pollerThread = new Thread(this.poller);
		pollerThread.start();
	}
	
	public void addServiceChain(NFVServiceChain serviceChain){
		this.serviceChainMap.put(serviceChain.serviceChainConfig.name, serviceChain);
		if(serviceChain.serviceChainConfig.name.equals("DATA")){
			this.indexMap = new ArrayList<Map<Integer, Integer>>();
			for(int i=0; i<serviceChain.serviceChainConfig.stages.size(); i++){
				this.indexMap.add(new HashMap<Integer, Integer>());
			}
			
			this.dpReactiveCounter = new int[serviceChain.serviceChainConfig.stages.size()];
		}
		else{
			this.cpReactiveCounter = new int[serviceChain.serviceChainConfig.stages.size()];
		}
	}
	
	private int searchIndexMap(int left, int right, Map<Integer, Integer> map){
		if(left>right){
			return -1;
		}
		else{
			if(!map.containsKey((left+right)/2)){
				return (left+right)/2;
			}
			else{
				int returnVal = searchIndexMap((left+right)/2+1, right, map);
				if(returnVal != -1){
					return returnVal;
				}
				else{
					return searchIndexMap(left, (left+right)/2-1, map);
				}
			}
		}
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
		this.serviceChainMap.get("DATA").initPathsArray(this.dcNum);
		this.serviceChainMap.get("CONTROL").initPathsArray(this.dcNum);
	}
	
	private void relayCreateInterDcTunnelMash(CreateInterDcTunnelMash req){
		this.mh.sendTo("vmAllocator", req);
	}
	
	private void handleCreateInterDcTunnelMashReply(){
		requester = context.socket(ZMQ.REQ);
		requester.connect("inproc://schSync");
		requester.send("TUNNELFINISH", 0);
		requester.recv(0);
	}
	
	private void proactiveScalingStart(){
		disableReactive();
		
		NFVServiceChain dpServiceChain = serviceChainMap.get("DATA");
		int dpProvision[] = dpServiceChain.getProvision();
		for(int i=0; i<this.dpReactiveCounter.length; i++){
			dpProvision[i]+=this.dpReactiveCounter[i];
		}
		
		NFVServiceChain cpServiceChain = serviceChainMap.get("CONTROL");
		int cpProvision[] = cpServiceChain.getProvision();
		for(int i=0; i<this.cpReactiveCounter.length; i++){
			cpProvision[i]+=this.cpReactiveCounter[i];
		}
		
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
		logger.info("ServiceChainHandler receives proactive start request, stops reactive scaling");
	}
	
	private void handleProactiveProvision(NFVServiceChain serviceChain, int newProvision[]){
		synchronized(serviceChain){
			int oldProvision[] = serviceChain.getProvision();
			if(serviceChain.serviceChainConfig.name.equals("DATA")){
				for(int i=0; i<this.dpReactiveCounter.length; i++){
					oldProvision[i]+=this.dpReactiveCounter[i];
				}
			}
			else{
				for(int i=0; i<this.cpReactiveCounter.length; i++){
					oldProvision[i]+=this.cpReactiveCounter[i];
				}
			}
			
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
			int[] scaleUpCounter = new int[newProvision.length];
			
			for(int i=0; i<oldProvision.length; i++){
				if(newProvision[i] > oldProvision[i]){
					//scale up
					//transform all buffer node into working node.
					NFVNode bufferNode = serviceChain.removeFromBqRear(i);
					while(bufferNode != null){
		
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
						bufferNode = serviceChain.removeFromBqRear(i);
					}
					
					//create new vms.
					int scaleUpNum = newProvision[i] - oldProvision[i];
					scaleUpCounter[i] = scaleUpNum;
					//for(int j=0; j<scaleUpNum; j++){
						
						//AllocateVmRequest newReq = new AllocateVmRequest(this.getId(),
						//		serviceChain.serviceChainConfig.name, i, null);
						//this.pendingMap.put(newReq.getUUID(), newReq);
						//this.mh.sendTo("vmAllocator", newReq);
						
					//}
				}
				else if(newProvision[i] < oldProvision[i]){
					//we need to maintain at least newProvision[i] working node
					//and oldProvision[i]-newProvision[i] buffer node
					int workingNodeNum = serviceChain.getWorkingNodeNum(i);
					
					if(workingNodeNum<newProvision[i]){
						//we need to release newProvision[i]-workingNodeNum buffer node
						int bfNodeToRelease = newProvision[i]-workingNodeNum;
						for(int j=0; j<bfNodeToRelease; j++){
							NFVNode bufferNode = serviceChain.removeFromBqRear(i);
							if(bufferNode!=null){
								serviceChain.addWorkingNode(bufferNode);
							}
						}
					}
					else if(workingNodeNum>newProvision[i]){
						//we need to transform workingNodeNum-newProvision[i] node to buffer node
						int scaleDownNum = workingNodeNum-newProvision[i];
						for(int j=0; j<scaleDownNum; j++){
							NFVNode workingNode = serviceChain.getNormalWorkingNode(i);
							if(workingNode == null){
								break;
							}
							else{
								boolean flag = serviceChain.removeWorkingNode(workingNode);
								if(
										(serviceChain.serviceChainConfig.name.equals("CONTROL"))&&
										(workingNode.vmInstance.stageIndex == 1)&&
										(workingNode.vmInstance.managementIp.equals("192.166.64.32")) ){
									flag = false;
									serviceChain.addWorkingNode(workingNode);
								}
								
								if(flag == true){
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
					}
					else{
						//do nothing
					}
				}
				else{
					//transform all buffer node into working node.
					NFVNode bufferNode = serviceChain.removeFromBqRear(i);
					while(bufferNode != null){
						
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
						bufferNode = serviceChain.removeFromBqRear(i);
					}
				}
			}
			
			while(!arrayAllZero(scaleUpCounter)){
				for(int i=0; i<scaleUpCounter.length; i++){
					if(scaleUpCounter[i]>0){
						AllocateVmRequest newReq = new AllocateVmRequest(this.getId(),
								serviceChain.serviceChainConfig.name, i, null);
						this.pendingMap.put(newReq.getUUID(), newReq);
						this.mh.sendTo("vmAllocator", newReq);
						scaleUpCounter[i]-=1;
					}
				}
			}
		}
	}
	
	private boolean arrayAllZero(int array[]){
		boolean allZero = true;
		for(int i=0; i<array.length; i++){
			if(array[i] != 0){
				allZero = false;
				break;
			}
		}
		return allZero;
	}
	
	private void handleProactiveScalingRequest(ProactiveScalingRequest req){
		pendingMap.clear();
		
		int newDpProvision[] = req.getLocalDpProvision();
		int newDpPaths[][][] = req.getDpPaths();
		
		for(int i=0; i<newDpPaths.length; i++){
			String print = "";
			for(int j=0; j<newDpPaths[i].length; j++){
				for(int k=0; k<newDpPaths[i][j].length; k++){
					print = print + new Integer(newDpPaths[i][j][k]).toString() + " ";
				}
				print = print + ": ";
			}
			System.out.println(print);
		}
		
		NFVServiceChain dpServiceChain = serviceChainMap.get("DATA");
		dpServiceChain.addNextDpPaths(newDpPaths);
		handleProactiveProvision(dpServiceChain, newDpProvision);
		
		int newCpProvision[] = req.getLocalCpProvision();
		NFVServiceChain cpServiceChain = serviceChainMap.get("CONTROL");
		handleProactiveProvision(cpServiceChain, newCpProvision);
		
		if(pendingMap.size() == 0){
			requester.send("COMPLETE", 0);
			requester.recv(0);
			enableReactive();
			logger.info("service chain handler finishes executing proactive scaling decision");
		}
	}
	
	private void handleAllocateVmReply(AllocateVmReply reply){
		
		AllocateVmRequest originalRequest = reply.getAllocateVmRequest();
		VmInstance vmInstance = reply.getVmInstance();
		
		if(vmInstance!=null){
			logger.info("receive AllocateVmReply for "+vmInstance.serviceChainConfig.name+" and stage "+
			Integer.toString(vmInstance.stageIndex));
			
			String serviceChainName = vmInstance.serviceChainConfig.name;
			if(this.serviceChainMap.containsKey(serviceChainName)){
				SubConnRequest request = new SubConnRequest(this.getId(),vmInstance.managementIp,
						"7776", "7775", vmInstance, originalRequest);
				this.mh.sendTo("subscriberConnector", request);
			}
		}
		else{
			if(pendingMap.containsKey(originalRequest.getUUID())){
				//This is an unallocated proactive scaling result
				pendingMap.remove(originalRequest.getUUID());
				if(pendingMap.size() == 0){
					requester.send("COMPLETE", 0);
					requester.recv(0);
					enableReactive();
					logger.info("service chain handler finishes executing proactive scaling decision");
				}
			}
			else if(errorMap.containsKey(originalRequest.getUUID())){
				errorMap.remove(originalRequest.getUUID());
				String errorIp = originalRequest.getErrorIp();
				errorIpMap.remove(errorIp);
			}
			else if(reactiveMap.containsKey(originalRequest.getUUID())){
				NFVServiceChain serviceChain = serviceChainMap.get(originalRequest.getChainName());
				synchronized(serviceChain){
					serviceChain.setScaleIndicator(originalRequest.getStageIndex(), false);
				}
				reactiveMap.remove(originalRequest.getUUID());
				if(originalRequest.getChainName().equals("DATA")){
					this.dpReactiveCounter[originalRequest.getStageIndex()]-=1;
				}
				else{
					this.cpReactiveCounter[originalRequest.getStageIndex()]-=1;
				}
			}
		}
	}
	
	private void addToServiceChain(NFVServiceChain serviceChain, NFVNode node, Message originalMessage){
		UUID uuid = originalMessage.getUUID();
		AllocateVmRequest originalRequest = (AllocateVmRequest)originalMessage;
		if(pendingMap.containsKey(uuid)){
			//one of the proactive scaling requests is finished.
			logger.info("node: "+node.vmInstance.managementIp+" stage: "+new Integer(node.vmInstance.stageIndex).toString()+
					" chain: "+node.vmInstance.serviceChainConfig.name+" is created by proactive scaling");
			synchronized(serviceChain){
				serviceChain.addToServiceChain(node);
				serviceChain.addWorkingNode(node);
			}
			logger.info("addWorkingNode finishes.");
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
				requester.send("COMPLETE", 0);
				logger.info("receiving ack from local controller");
				requester.recv(0);
				logger.info("ack is received");
				enableReactive();
				logger.info("service chain handler finishes executing proactive scaling decision");
			}
		}
		else if(errorMap.containsKey(uuid)){
			//receive an error node processing
			synchronized(serviceChain){
				serviceChain.addToServiceChain(node);
				serviceChain.addWorkingNode(node);
			}
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
			errorMap.remove(uuid);
			String errorIp = originalRequest.getErrorIp();
			errorIpMap.remove(errorIp);
			
			logger.info(
					"node: "+node.vmInstance.managementIp+
					" stage: "+new Integer(node.vmInstance.stageIndex).toString()+
					" chain: "+node.vmInstance.serviceChainConfig.name+
					" is error, added to destroy node.!");
			synchronized(serviceChain){
				NFVNode errorNode = serviceChain.getNode(errorIp);
				boolean flag = serviceChain.removeWorkingNode(errorNode);
				if(flag == true){
					serviceChain.addDestroyNode(errorNode);
					serviceChain.removeFromServiceChain(errorNode);
				}
				else{
					logger.error("FATAL ERROR, the error node handling has errors.");
				}
			}
		}
		else if(reactiveMap.containsKey(uuid)){
			//a reactive scaling request is finished
			reactiveMap.remove(uuid);
			if(node.vmInstance.serviceChainConfig.name.equals("DATA")){
				this.dpReactiveCounter[node.vmInstance.stageIndex]-=1;
			}
			else{
				this.cpReactiveCounter[node.vmInstance.stageIndex]-=1;
			}
			
			logger.info("node: "+node.vmInstance.managementIp+" stage: "+new Integer(node.vmInstance.stageIndex).toString()+
					" chain: "+node.vmInstance.serviceChainConfig.name+" is created by reactive scaling");
			synchronized(serviceChain){
				serviceChain.addToServiceChain(node);
				serviceChain.addWorkingNode(node);
			}
			
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
			serviceChain.setScaleIndicator(node.vmInstance.stageIndex, false);
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
		
		String managementIp = vmInstance.managementIp;
		Socket subscriber1 = reply.getSubscriber1();
		this.poller.register(new Pair<String, Socket>(managementIp+":1", subscriber1));
		
		if(node.vmInstance.serviceChainConfig.nVmInterface == 3){
			//This a dataplane node, assign a unique identifier to it.
			int newIndex = searchIndexMap(11, 255, indexMap.get(node.vmInstance.stageIndex));
			if(newIndex!=-1){
				indexMap.get(node.vmInstance.stageIndex).put(newIndex, 0);
			}
			node.setIndex(newIndex);
		}
		
		//We push a static flow rule here.
		if(node.vmInstance.serviceChainConfig.nVmInterface==3){
			//this is a dataplane node, push static flow rule for it
			pushStaticFlowRule(node);
		}
		
		addToServiceChain(serviceChain, node, originalMessage);
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
					if((dpServiceChain.getWorkingNodeNum(i)+dpServiceChain.bqSize(i))==0){
						dpServiceChain.addWorkingNode(destroyNode);
					}
					else{
						logger.info("a DATA node will be destroyed");
						dpServiceChain.addDestroyNode(destroyNode);
						dpServiceChain.removeFromServiceChain(destroyNode);
					}
					destroyNode = dpServiceChain.removeFromBqHead(i);
				}
			}
		}
		
		synchronized(cpServiceChain){
			for(int i=0; i<cpServiceChain.serviceChainConfig.stages.size(); i++){
				destroyNode = cpServiceChain.removeFromBqHead(i);
				while(destroyNode != null){
					if((cpServiceChain.getWorkingNodeNum(i)+cpServiceChain.bqSize(i))==0){
						cpServiceChain.addWorkingNode(destroyNode);
					}
					else{
						logger.info("a CONTROL node will be destroyed");
						cpServiceChain.addDestroyNode(destroyNode);
						cpServiceChain.removeFromServiceChain(destroyNode);
					}
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
		//logger.info("{}", print);
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
					
					NFVNode node = chain.getNode(managementIp);
					if((node.getState() == NFVNode.ERROR)){
						if(!this.errorIpMap.containsKey(node.vmInstance.managementIp)&&chain.isWorkingNode(node)){
							AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
					                                  node.vmInstance.serviceChainConfig.name,
					                                               node.vmInstance.stageIndex,
					                                             node.vmInstance.managementIp);
							this.errorMap.put(newRequest.getUUID(), newRequest);
							this.errorIpMap.put(node.vmInstance.managementIp, 0);
							this.mh.sendTo("vmAllocator", newRequest);
						}
					}
					else{
						if(reactiveStart == true){
							Map<String, NFVNode> stageMap = chain.getStageMap(node.vmInstance.stageIndex);
							
							int nOverload = 0;
							int nNotOverload = 0;
							for(String ip : stageMap.keySet()){
								NFVNode n = stageMap.get(ip);
								if(n.getState() == NFVNode.OVERLOAD){
									nOverload += 1;
								}
								else{
									nNotOverload += 1;
								}
							}
							
							if( (nOverload > nNotOverload)&&
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
											                              node.vmInstance.stageIndex,
											                              null);
									
									this.reactiveMap.put(newRequest.getUUID(), newRequest);
									if(node.vmInstance.serviceChainConfig.name.equals("DATA")){
										this.dpReactiveCounter[node.vmInstance.stageIndex]+=1;
									}
									else{
										this.cpReactiveCounter[node.vmInstance.stageIndex]+=1;
									}
									
									this.mh.sendTo("vmAllocator", newRequest);
								}
							}
						}
					}
					
					ArrayList<String> list = new ArrayList<String>();
					for(String key : chain.destroyNodeMap.keySet()){
						NFVNode destroyNode = chain.destroyNodeMap.get(key);
						logger.info(
							"node: "+destroyNode.vmInstance.managementIp+
							" stage: "+new Integer(destroyNode.vmInstance.stageIndex).toString()+
							" chain: "+destroyNode.vmInstance.serviceChainConfig.name+
							" is destroyed!");
						if(destroyNode.vmInstance.serviceChainConfig.nVmInterface==3){
							//This is a dataplane node, we need to remove its index from indexmap
							deleteStaticFlowRule(destroyNode);
							int index = destroyNode.getIndex();
							this.indexMap.get(destroyNode.vmInstance.stageIndex).remove(index);
						}
						else{
							//TODO:remove DNS here, or it seems that we don't really need to
							//because dns record has already been removed
						}
						
						this.poller.unregister(destroyNode.getManagementIp()+":1");
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
	
	public String getRegIp(){
		NFVServiceChain cpServiceChain = this.serviceChainMap.get("CONTROL");
		String regIp = "";
		synchronized(cpServiceChain){
			NFVNode pcscfNode = cpServiceChain.randomlyGetWorkingNode(0);
			regIp = pcscfNode.vmInstance.managementIp;
		}
		
		return regIp;
	}
	
	private void installStaticRuleWithoutMac(IOFSwitch sw, int stageIndex, int index, int outPort){
		Match.Builder mb = sw.getOFFactory().buildMatch();
		
		byte newDstAddr[] = new byte[4];
		byte mask[] = new byte[4];
		for(int i=0; i<4; i++){
			if((i)==stageIndex){
				newDstAddr[i] = (byte)index;
				mask[i] = ((byte)255);
			}
			else{
				newDstAddr[i] = 0;
				mask[i] = ((byte)0);
			}
		}
		
		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
        .setMasked(MatchField.IPV4_DST, IPv4Address.of(newDstAddr), IPv4Address.of(mask));
		Match flowMatch = mb.build();
		
		List<OFAction> actionList = new ArrayList<OFAction>();	
		OFActions actions = sw.getOFFactory().actions();
		actionList.add(actions.output(OFPort.of(outPort), Integer.MAX_VALUE));
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
		fmb.setHardTimeout(0);
		fmb.setIdleTimeout(0);
		fmb.setBufferId(OFBufferId.NO_BUFFER);
		fmb.setCookie(U64.of(8617));
		fmb.setPriority(5);
		fmb.setOutPort(OFPort.of(outPort));
		fmb.setActions(actionList);
		fmb.setMatch(flowMatch);
		Set<OFFlowModFlags> sfmf = new HashSet<OFFlowModFlags>();
		sfmf.add(OFFlowModFlags.SEND_FLOW_REM);
		fmb.setFlags(sfmf);
		OFFlowMod flowMod = fmb.build();
		
		sw.write(flowMod);
		sw.flush();
	}
	
	private void installStaticRule(IOFSwitch sw, int stageIndex, int index, int outPort, String macAddr){
		Match.Builder mb = sw.getOFFactory().buildMatch();
		
		byte newDstAddr[] = new byte[4];
		byte mask[] = new byte[4];
		for(int i=0; i<4; i++){
			if((i)==stageIndex){
				newDstAddr[i] = (byte)index;
				mask[i] = ((byte)255);
			}
			else{
				newDstAddr[i] = 0;
				mask[i] = ((byte)0);
			}
		}
		
		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
        .setMasked(MatchField.IPV4_DST, IPv4Address.of(newDstAddr), IPv4Address.of(mask));
		Match flowMatch = mb.build();
		
		List<OFAction> actionList = new ArrayList<OFAction>();	
		OFActions actions = sw.getOFFactory().actions();
		OFOxms oxms = sw.getOFFactory().oxms();
		actionList.add(actions.setField(oxms.ethDst(MacAddress.of(macAddr))));
		actionList.add(actions.output(OFPort.of(outPort), Integer.MAX_VALUE));
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
		fmb.setHardTimeout(0);
		fmb.setIdleTimeout(0);
		fmb.setBufferId(OFBufferId.NO_BUFFER);
		fmb.setCookie(U64.of(8617));
		fmb.setPriority(5);
		fmb.setOutPort(OFPort.of(outPort));
		fmb.setActions(actionList);
		fmb.setMatch(flowMatch);
		Set<OFFlowModFlags> sfmf = new HashSet<OFFlowModFlags>();
		sfmf.add(OFFlowModFlags.SEND_FLOW_REM);
		fmb.setFlags(sfmf);
		OFFlowMod flowMod = fmb.build();
		sw.write(flowMod);
		sw.flush();
	}
	
	void pushStaticFlowRule(NFVNode dpNode){
		int stageIndex = dpNode.vmInstance.stageIndex;
		for(int hostServerIndex=0; hostServerIndex<hostServerList.size(); hostServerIndex++){
			HostServer hostServer = hostServerList.get(hostServerIndex);
			
			DatapathId hitSwitchDpid = DatapathId.of(hostServer.serviceChainDpidMap.get("DATA").get(stageIndex));
			DatapathId nodeSwitchDpid = DatapathId.of(dpNode.getBridgeDpid(0));
			
			if(hitSwitchDpid.equals(nodeSwitchDpid)){
				IOFSwitch hitSwitch = switchService.getSwitch(hitSwitchDpid);
				
				if(dpNode.getIndex()!=-1){
					installStaticRule(hitSwitch, stageIndex, dpNode.getIndex(), dpNode.getPort(0), dpNode.getMacAddress(0));
				}
			}
			else{
				IOFSwitch hitSwitch = switchService.getSwitch(hitSwitchDpid);
				IOFSwitch nodeSwitch = switchService.getSwitch(nodeSwitchDpid);
				
				if(dpNode.getIndex()!=-1){
					HostServer remoteHostServer = dpidHostServerMap.get(nodeSwitchDpid);
					String remoteServerIp = remoteHostServer.hostServerConfig.managementIp;
					int localPort = hostServer.tunnelPortMap.get(remoteServerIp).intValue();
					
					installStaticRuleWithoutMac(hitSwitch, stageIndex, dpNode.getIndex(), localPort);
					
					installStaticRule(nodeSwitch, stageIndex, dpNode.getIndex(), dpNode.getPort(0), dpNode.getMacAddress(0));
				}
			}
		}
	}
	
	private void deleteStaticRule(IOFSwitch sw, int stageIndex, int index, int outPort){
		Match.Builder mb = sw.getOFFactory().buildMatch();
		
		byte newDstAddr[] = new byte[4];
		byte mask[] = new byte[4];
		for(int i=0; i<4; i++){
			if((i)==stageIndex){
				newDstAddr[i] = (byte)index;
				mask[i] = ((byte)255);
			}
			else{
				newDstAddr[i] = 0;
				mask[i] = ((byte)0);
			}
		}
		
		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
        .setMasked(MatchField.IPV4_DST, IPv4Address.of(newDstAddr), IPv4Address.of(mask));
		Match flowMatch = mb.build();
		
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowDelete();
		fmb.setHardTimeout(0);
		fmb.setIdleTimeout(0);
		fmb.setBufferId(OFBufferId.NO_BUFFER);
		fmb.setCookie(U64.of(8617));
		fmb.setPriority(5);
		fmb.setOutPort(OFPort.ANY);
		fmb.setMatch(flowMatch);
		Set<OFFlowModFlags> sfmf = new HashSet<OFFlowModFlags>();
		fmb.setFlags(sfmf);
		OFFlowMod flowMod = fmb.build();
		sw.write(flowMod);
		sw.flush();
	}
	
	void deleteStaticFlowRule(NFVNode dpNode){
		int stageIndex = dpNode.vmInstance.stageIndex;
		for(int hostServerIndex=0; hostServerIndex<hostServerList.size(); hostServerIndex++){
			HostServer hostServer = hostServerList.get(hostServerIndex);
			
			DatapathId hitSwitchDpid = DatapathId.of(hostServer.serviceChainDpidMap.get("DATA").get(stageIndex));
			DatapathId nodeSwitchDpid = DatapathId.of(dpNode.getBridgeDpid(0));
			
			if(hitSwitchDpid.equals(nodeSwitchDpid)){
				IOFSwitch hitSwitch = switchService.getSwitch(hitSwitchDpid);
				
				if(dpNode.getIndex()!=-1){
					deleteStaticRule(hitSwitch, stageIndex, dpNode.getIndex(), dpNode.getPort(0));
				}
			}
			else{
				IOFSwitch hitSwitch = switchService.getSwitch(hitSwitchDpid);
				IOFSwitch nodeSwitch = switchService.getSwitch(nodeSwitchDpid);
				
				if(dpNode.getIndex()!=-1){
					HostServer remoteHostServer = dpidHostServerMap.get(nodeSwitchDpid);
					String remoteServerIp = remoteHostServer.hostServerConfig.managementIp;
					int localPort = hostServer.tunnelPortMap.get(remoteServerIp).intValue();
					
					deleteStaticRule(hitSwitch, stageIndex, dpNode.getIndex(), localPort);
					
					deleteStaticRule(nodeSwitch, stageIndex, dpNode.getIndex(), dpNode.getPort(0));
				}
			}
		}
	}
}