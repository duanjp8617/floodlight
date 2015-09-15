package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.concurrent.LinkedBlockingQueue;

import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.message.Pending;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVException;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVNode;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVServiceChain;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.ServiceChainConfig;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer.VmInstance;
import net.floodlightcontroller.nfvtest.nfvutils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class ServiceChainHandler extends MessageProcessor {
	private final HashMap<String, NFVServiceChain> serviceChainMap;
	private final HashMap<UUID, Pending> pendingMap;
	private NFVZmqPoller poller;
	
	private final HashMap<String, HostServer> hostServerMap;
	private final Context zmqContext;
	
	private SwitchStatPoller statPoller;
	private final IOFSwitchService switchService;
	private final VmAllocator vmAllocator;
	
	private int dcNum;
	
	private final DcLinkGraph dcLinkGraph;
	
	private final HashMap<String, HashMap<String, List<HashMap<String, VmInstance>>>> serverVmMap;

	public ServiceChainHandler(String id, Context zmqContext, IOFSwitchService switchService,
							   VmAllocator vmAllocator){
		this.id = id;
		this.queue = new LinkedBlockingQueue<Message>();
		serviceChainMap = new HashMap<String, NFVServiceChain>();
		pendingMap = new HashMap<UUID, Pending>();
		hostServerMap = new HashMap<String, HostServer>();
		this.zmqContext = zmqContext;
		this.switchService = switchService;
		this.dcNum = 0;
		this.vmAllocator = vmAllocator;
		
		this.dcLinkGraph = new DcLinkGraph(this.dcNum);
		this.serverVmMap = new HashMap<String, HashMap<String, List<HashMap<String, VmInstance>>>>();
	}
	
	public void startSwitchStatPoller(){
		this.statPoller = new SwitchStatPoller(this.mh, this.switchService, this.hostServerMap);
		Thread thread = new Thread(this.statPoller);
		thread.start();
	}
	
	public void startPollerThread(){
		poller = new NFVZmqPoller(this.mh);
		Thread pollerThread = new Thread(this.poller);
		pollerThread.start();
	}
	
	public NFVZmqPoller getZmqPoller(){
		return this.poller;
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
		if(m instanceof ServerToChainHandlerRequest){
			ServerToChainHandlerRequest req = (ServerToChainHandlerRequest)m;
			addServerToChainHandler(req);
		}
		if(m instanceof DcLinkStat){
			handleDcLinkStat((DcLinkStat)m);
		}
	}
	
	private void initServiceChain(InitServiceChainRequset originalRequest){
		NFVServiceChain serviceChain = originalRequest.getServiceChain();
		Pending pending = new Pending(serviceChain.serviceChainConfig.stages.size()*this.dcNum, 
									  originalRequest);
		for(int i=0; i<this.dcNum; i++){
			for(int j=0; j<serviceChain.serviceChainConfig.stages.size(); j++){
				AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
												serviceChain.serviceChainConfig.name,
												j, false, i, null);
				this.pendingMap.put(newRequest.getUUID(), pending);
				this.mh.sendTo("vmAllocator", newRequest);
			}
		}
	}
	
	private void allocateVm(AllocateVmRequest request){
		AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(), request.getChainName(),
											request.getStageIndex(), request.getIsBufferNode(), request.getDcIndex(), null);
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
					if(vmInstance==null){
						throw new NFVException("Fatal: not enough resource at initialization phase");
					}
					else{
						SubConnRequest request = new SubConnRequest(this.getId(),vmInstance.managementIp,
																"7776", "7775", vmInstance);
						this.mh.sendTo("subscriberConnector", request);
					}
				}
			}
		}
		else{
			pending.addReply(newReply);
			VmInstance vmInstance = newReply.getVmInstance();
			if(vmInstance == null){
				System.out.println("Not enough resource");
			}
			else{
				String serviceChainName = vmInstance.serviceChainConfig.name;
				if(this.serviceChainMap.containsKey(serviceChainName)){
					SubConnRequest request = new SubConnRequest(this.getId(),vmInstance.managementIp,
							"7776", "7775", vmInstance);
					this.mh.sendTo("subscriberConnector", request);
				}
			}
		}
	}
	
	private void handleSubConnReply(SubConnReply reply){
		SubConnRequest request = reply.getSubConnRequest();
		VmInstance vmInstance = request.getVmInstance();
		
		if(vmInstance.serviceChainConfig.nVmInterface == 3){
			String serviceChainName = vmInstance.serviceChainConfig.name;
			NFVNode node = new NFVNode(vmInstance);
			
			synchronized(this.serviceChainMap.get(serviceChainName)){
				this.serviceChainMap.get(serviceChainName).addNodeToChain(node);
				this.serviceChainMap.get(serviceChainName).scaleIndicators
				.get(vmInstance.hostServerConfig.dcIndex)[vmInstance.stageIndex]=false;
			}
			
			String managementIp = request.getManagementIp();
			Socket subscriber1 = reply.getSubscriber1();
			this.poller.register(new Pair<String, Socket>(managementIp+":1", subscriber1));
			
			String serverIp = vmInstance.hostServerConfig.managementIp;
			String chainName = vmInstance.serviceChainConfig.name;
			int stageIndex = vmInstance.stageIndex;
			this.serverVmMap.get(serverIp).get(chainName)
			                .get(stageIndex).put(vmInstance.managementIp, vmInstance);
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
		NFVNode node = new NFVNode(vmInstance);
		
		synchronized(this.serviceChainMap.get(serviceChainName)){
			this.serviceChainMap.get(serviceChainName).addNodeToChain(node);
			this.serviceChainMap.get(serviceChainName).scaleIndicators
				.get(vmInstance.hostServerConfig.dcIndex)[vmInstance.stageIndex]=false;
		}
	
		String managementIp = vmInstance.managementIp;
		Socket subscriber1 = request.getSocket1();
		Socket subscriber2 = request.getSocket2();
		
		this.poller.register(new Pair<String, Socket>(managementIp+":1", subscriber1));
		this.poller.register(new Pair<String, Socket>(managementIp+":2", subscriber2));
		
		String serverIp = vmInstance.hostServerConfig.managementIp;
		String chainName = vmInstance.serviceChainConfig.name;
		int stageIndex = vmInstance.stageIndex;
		this.serverVmMap.get(serverIp).get(chainName)
		                .get(stageIndex).put(vmInstance.managementIp, vmInstance);
	}
	
	private void statUpdate(StatUpdateRequest request){
		ArrayList<String> statList = request.getStatList();
		String managementIp = request.getManagementIp();
		
		if(this.hostServerMap.containsKey(managementIp)){
			handleServerStat(managementIp, statList);
			return;
		}
		
		for(String chainName : this.serviceChainMap.keySet()){
			NFVServiceChain chain = this.serviceChainMap.get(chainName);
			synchronized(chain){
				if(chain.hasNode(managementIp)&&(chain.serviceChainConfig.nVmInterface==3)){
					chain.updateDataNodeStat(managementIp, statList);
					
					NFVNode node = chain.getNode(managementIp);
					int stageIndex = node.vmInstance.stageIndex;
					int dcIndex = node.vmInstance.hostServerConfig.dcIndex;
					Map<String, NFVNode> stageMap = chain.getStageMap(dcIndex, stageIndex);

					int nOverload = 0;
					for(String ip : stageMap.keySet()){
						NFVNode n = stageMap.get(ip);
						if(n.getState() == NFVNode.OVERLOAD){
							nOverload += 1;
						}
					}
					
					if(nOverload == 0){
						if(chain.scaleDownList.get(dcIndex).get(stageIndex).size()!=0){
							Map<String, Integer> stageScaleDownMap = 
									            chain.scaleDownList.get(dcIndex).get(stageIndex);
							List<String> deletedNodeIndexList = new ArrayList<String>();
							
							for(String ip : stageScaleDownMap.keySet()){
								NFVNode n = chain.getNode(ip);
								
								if(n.getActiveFlows() == 0){
									VmInstance vmInstance = n.vmInstance;
									String nodeServerIp = vmInstance.hostServerConfig.managementIp;
									String nodeChainName = vmInstance.serviceChainConfig.name;
									int nodeStageIndex = vmInstance.stageIndex;
									this.serverVmMap.get(nodeServerIp).get(nodeChainName)
									                .get(nodeStageIndex).remove(vmInstance.managementIp);
									
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
								chain.scaleDownCounter.get(dcIndex)[stageIndex] = System.currentTimeMillis();
							}
						}
						else{
							if((System.currentTimeMillis()-
								chain.scaleDownCounter.get(dcIndex)[stageIndex])>=15000){
								//The stage has been relatively idle for 15000s, put some 
								//nodes to the scaleDownList
								if(stageMap.size()>1){
									List<String> serverList = this.vmAllocator.getHostServerList(dcIndex);
									String nodeToScaleDown = null;
									
									for(int i=0; i<serverList.size(); i++){
										for(String mIp : stageMap.keySet()){
											NFVNode currentNode = stageMap.get(mIp);
											if(currentNode.vmInstance.hostServerConfig.managementIp
													.equals(serverList.get(i))){
												nodeToScaleDown = mIp;
												break;
											}
										}
									}
									
									/*if(nodeToScaleDown==null){
										String[] ipList = stageMap.keySet()
								                 	.toArray(new String[stageMap.size()]);
										nodeToScaleDown = ipList[0];
									}*/
									
									chain.scaleDownList.get(dcIndex).get(stageIndex).put(nodeToScaleDown, new Integer(0));
									System.out.println("!!!Scale Down: Node "+nodeToScaleDown+" is put into the scaleDownList");
								}
								chain.scaleDownCounter.get(dcIndex)[stageIndex] = System.currentTimeMillis();
							}						
						}
					}
					
					if( (nOverload == stageMap.size())&&
					    (!chain.scaleIndicators.get(dcIndex)[node.vmInstance.stageIndex]) ){
						chain.scaleIndicators.get(dcIndex)[node.vmInstance.stageIndex] = true;
						AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
								                  node.vmInstance.serviceChainConfig.name,
								                              stageIndex, false, dcIndex, null);
						Pending pending = new Pending(1, null);
						this.pendingMap.put(newRequest.getUUID(), pending);
						this.mh.sendTo("vmAllocator", newRequest);
						
						chain.scaleDownList.get(dcIndex).get(stageIndex).clear();
						chain.scaleDownCounter.get(dcIndex)[stageIndex] = System.currentTimeMillis();
					}
					else if((nOverload > 0)&&(nOverload < stageMap.size())){
						chain.scaleDownList.get(dcIndex).get(stageIndex).clear();
						chain.scaleDownCounter.get(dcIndex)[stageIndex] = System.currentTimeMillis();
					}
					
					break;
				}
				if(chain.hasNode(managementIp)&&(chain.serviceChainConfig.nVmInterface==2)){
					chain.updateControlNodeStat(managementIp, statList);
					NFVNode node = chain.getNode(managementIp);
					int dcIndex = node.vmInstance.hostServerConfig.dcIndex;
					
					if(node.vmInstance.stageIndex == 0){
						Map<String, NFVNode> stageMap = chain.getStageMap(dcIndex, 0);
						
						int nOverload = 0;
						for(String ip : stageMap.keySet()){
							NFVNode n = stageMap.get(ip);
							if(n.getTranState() == NFVNode.OVERLOAD){
								nOverload += 1;
							}
						}
						
						if(nOverload == stageMap.size()){
							//Let's find out which stage needs scaling.
							controlPlaneScaleUp(chain, dcIndex);
						}
						else{
							nOverload = 0;
							for(String ip : stageMap.keySet()){
								NFVNode n = stageMap.get(ip);
								if(n.getState() == NFVNode.OVERLOAD){
									nOverload += 1;
								}
							}
							
							if(nOverload == 0){
								controlPlaneScaleDown(chain, dcIndex, 0);
								controlPlaneScaleDown(chain, dcIndex, 1);
							}
							else{
								Map<String, Integer> stageScaleDownMap = 
										chain.scaleDownList.get(dcIndex).get(0);
								for(String mIp : stageScaleDownMap.keySet()){
									NFVNode n = chain.getNode(mIp);
									DNSAddRequest addRequest = new DNSAddRequest(this.getId(),
									                                              "bono.cw.t",
									                                n.vmInstance.operationIp);
									this.mh.sendTo("dnsUpdator", addRequest);
								}
								chain.scaleDownList.get(dcIndex).get(0).clear();
								chain.scaleDownCounter.get(dcIndex)[0] = System.currentTimeMillis();
								
								stageScaleDownMap = 
										chain.scaleDownList.get(dcIndex).get(1);
								for(String mIp : stageScaleDownMap.keySet()){
									NFVNode n = chain.getNode(mIp);
									DNSAddRequest addRequest = new DNSAddRequest(this.getId(),
									                                              "sprout.cw.t",
									                                n.vmInstance.operationIp);
									this.mh.sendTo("dnsUpdator", addRequest);
								}
								chain.scaleDownList.get(dcIndex).get(1).clear();
								chain.scaleDownCounter.get(dcIndex)[1] = System.currentTimeMillis();
							}
						}
					}
					
					break;
				}
			}
		}
	}
	
	private void controlPlaneScaleUp(NFVServiceChain chain, int dcIndex){
		
		int nBonoOverload = 0;
		Map<String, NFVNode> bonoMap = chain.getStageMap(dcIndex, 0);
		for(String ip : bonoMap.keySet()){
			NFVNode n = bonoMap.get(ip);
			if(n.getState() == NFVNode.OVERLOAD){
				nBonoOverload += 1;
			}
		}
		
		int nSproutOverload = 0;
		Map<String, NFVNode> sproutMap = chain.getStageMap(dcIndex, 1);
		for(String ip : sproutMap.keySet()){
			NFVNode n = sproutMap.get(ip);
			if(n.getState() == NFVNode.OVERLOAD){
				nSproutOverload += 1;
			}
		}
		
		if((nBonoOverload==bonoMap.size())&&(!chain.scaleIndicators.get(dcIndex)[0])){
			chain.scaleIndicators.get(dcIndex)[0]=true;;
			AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
	                  							chain.serviceChainConfig.name,
	                  							0, false, dcIndex, null);
			Pending pending = new Pending(1, null);
			this.pendingMap.put(newRequest.getUUID(), pending);
			this.mh.sendTo("vmAllocator", newRequest);
		}
		
		if((nSproutOverload==sproutMap.size())&&(!chain.scaleIndicators.get(dcIndex)[1])){
			chain.scaleIndicators.get(dcIndex)[1] = true;
			AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
												chain.serviceChainConfig.name,
												1, false, dcIndex, null);
			Pending pending = new Pending(1, null);
			this.pendingMap.put(newRequest.getUUID(), pending);
			this.mh.sendTo("vmAllocator", newRequest);
		}
	}
	
	private void controlPlaneScaleDown(NFVServiceChain chain, int dcIndex, int stageIndex){
		Map<String, NFVNode> stageMap = chain.getStageMap(dcIndex, stageIndex);
		if(chain.scaleDownList.get(dcIndex).get(stageIndex).size()!=0){
			if((System.currentTimeMillis()-
					chain.scaleDownCounter.get(dcIndex)[stageIndex])>=15000){
				Map<String, Integer> stageScaleDownMap = 
					            	chain.scaleDownList.get(dcIndex).get(stageIndex);
				List<String> deletedNodeIndexList = new ArrayList<String>();
			
				for(String ip : stageScaleDownMap.keySet()){
					NFVNode n = chain.getNode(ip);
					
					VmInstance vmInstance = n.vmInstance;
					String nodeServerIp = vmInstance.hostServerConfig.managementIp;
					String nodeChainName = vmInstance.serviceChainConfig.name;
					int nodeStageIndex = vmInstance.stageIndex;
					this.serverVmMap.get(nodeServerIp).get(nodeChainName)
					                .get(nodeStageIndex).remove(vmInstance.managementIp);
					
				
					System.out.println("!!! ScaleDown: Node "+ip+" is deleted from the chain");
					chain.deleteNodeFromChain(n);
					deletedNodeIndexList.add(ip);
					
					//Do some other thing.
					this.poller.unregister(n.getManagementIp()+":1");
					this.poller.unregister(n.getManagementIp()+":2");
					DeallocateVmRequest deallocationRequest = 
							new DeallocateVmRequest(this.getId(), n.vmInstance);
					this.mh.sendTo("vmAllocator", deallocationRequest);
				}
			
				for(int i=0; i<deletedNodeIndexList.size(); i++){
					stageScaleDownMap.remove(deletedNodeIndexList.get(i));
				}
			
				if(stageScaleDownMap.size() == 0){
					chain.scaleDownCounter.get(dcIndex)[stageIndex] = System.currentTimeMillis();
				}
			}
		}
		else{
			if((System.currentTimeMillis()-
						chain.scaleDownCounter.get(dcIndex)[stageIndex])>=15000){
				//The stage has been relatively idle for 15000s, put some 
				//nodes to the scaleDownList
				if(stageMap.size()>1){
					List<String> serverList = this.vmAllocator.getHostServerList(dcIndex);
					String nodeToScaleDown = null;
					
					for(int i=0; i<serverList.size(); i++){
						for(String mIp : stageMap.keySet()){
							NFVNode currentNode = stageMap.get(mIp);
							if(currentNode.vmInstance.hostServerConfig.managementIp
										.equals(serverList.get(i))){
								nodeToScaleDown = mIp;
								break;
							}
						}
					}
					
					/*if(nodeToScaleDown==null){
						String[] ipList = stageMap.keySet()
				                 	.toArray(new String[stageMap.size()]);
						nodeToScaleDown = ipList[0];
					}*/
					
					chain.scaleDownList.get(dcIndex).get(stageIndex).put(nodeToScaleDown, new Integer(0));
					
					System.out.println("!!!Scale Down: Node "+nodeToScaleDown+" is put into the scaleDownList");
					
					DNSRemoveRequest request = new DNSRemoveRequest(this.getId(),
							           (stageIndex==0)?"bono.cw.t":"sprout.cw.t",
						   stageMap.get(nodeToScaleDown).vmInstance.operationIp);
					this.mh.sendTo("dnsUpdator", request);
				}
				chain.scaleDownCounter.get(dcIndex)[stageIndex] = System.currentTimeMillis();
			}						
		}
	}
	
	private void addServerToChainHandler(ServerToChainHandlerRequest request){
		HostServer hostServer = request.getHostServer();
		if(this.dcNum == 0){
			this.dcNum = hostServer.controllerConfig.dcNum;
		}
		
		Socket subscriber = this.zmqContext.socket(ZMQ.SUB);
		subscriber.monitor("inproc://monitorServerConnection", ZMQ.EVENT_CONNECTED);
		
		Socket monitor = zmqContext.socket(ZMQ.PAIR);
		monitor.setReceiveTimeOut(5000);
		monitor.connect("inproc://monitorServerConnection");
		ZMQ.Event event;	
		
		subscriber.connect("tcp://"+hostServer.hostServerConfig.managementIp+":"+"7776");
		
		event = ZMQ.Event.recv(monitor);
    	
    	if((event != null)&&(event.getEvent() == ZMQ.EVENT_CONNECTED)){
    		monitor.close();
    		subscriber.subscribe("".getBytes());
    		this.poller.register(new Pair<String, Socket> (hostServer.hostServerConfig.managementIp+":1"
                    ,subscriber));
    		this.hostServerMap.put(hostServer.hostServerConfig.managementIp, hostServer); 	
    	}
    	else{
    		System.out.println("HostServer connection failure");
    	}
    	
    	HashMap<String, List<HashMap<String, VmInstance>>> vmMap = 
    			new HashMap<String, List<HashMap<String, VmInstance>>>();
		for(String chainName : hostServer.serviceChainConfigMap.keySet()){
			ServiceChainConfig chainConfig = hostServer.serviceChainConfigMap.get(chainName);
			List<HashMap<String, VmInstance>> chainList = new ArrayList<HashMap<String, VmInstance>>();
			for(int i=0; i<chainConfig.stages.size(); i++){
				HashMap<String, VmInstance> stageMap = new HashMap<String, VmInstance>();
				chainList.add(stageMap);
			}
			vmMap.put(chainName, chainList);
		}
		this.serverVmMap.put(hostServer.hostServerConfig.managementIp, vmMap);
	}
	
	private void handleServerStat(String managementIp, ArrayList<String> statList){
		String eth0 = statList.get(8);
		String[] eth0StatArray = eth0.trim().split("\\s+");
		long eth0RecvBytes = Long.parseLong(eth0StatArray[0]);
		long eth0SendBytes = Long.parseLong(eth0StatArray[8]);
		if(this.hostServerMap.containsKey(managementIp)){
			HostServer hostServer = this.hostServerMap.get(managementIp);
			hostServer.updateNodeProperty(new Long(eth0RecvBytes),new Long(eth0SendBytes));
			
			//Start checking whether there is bandwidth overload.
			if(hostServer.getState()==NFVNode.OVERLOAD){
				int dcIndex = hostServer.hostServerConfig.dcIndex;
				List<String> serverList = this.vmAllocator.getHostServerList(dcIndex);
				int position = 0;
				for(int i=0; i<serverList.size(); i++){
					if(serverList.get(i).equals(hostServer.hostServerConfig.managementIp)){
						position = i;
						break;
					}
				}
				serverList.remove(position);
				HashMap<String, boolean[]> recordMap = new HashMap<String, boolean[]>();
				String dataPlaneChainName = null;
				String controlPlaneChainName = null;
				
				for(String chainName : hostServer.serviceChainConfigMap.keySet()){
					int size = hostServer.serviceChainConfigMap.get(chainName).stages.size();
					boolean[] array = new boolean[size];
					for(int i=0; i<size; i++){
						array[i] = false;
					}
					
					recordMap.put(chainName, array);
					
					if(hostServer.serviceChainConfigMap.get(chainName).nVmInterface==3){
						dataPlaneChainName = chainName;
					}
					if(hostServer.serviceChainConfigMap.get(chainName).nVmInterface==2){
						controlPlaneChainName = chainName;
					}
				}
				
				for(int i=0; i<serverList.size(); i++){
					HostServer server = this.hostServerMap.get(serverList.get(i));
					String serverIp = server.hostServerConfig.managementIp;
					for(String chainName : server.serviceChainConfigMap.keySet()){
						List<HashMap<String, VmInstance>> list = 
								this.serverVmMap.get(serverIp).get(chainName);
						for(int j=0; j<list.size(); j++){
							if(!list.get(j).isEmpty()){
								recordMap.get(chainName)[j]=true;
							}
						}
					}
				}
				
				boolean[] dataPlaneArray = recordMap.get(dataPlaneChainName);
				for(int i=0; i<dataPlaneArray.length; i++){
					if(!dataPlaneArray[i]){
						NFVServiceChain chain = this.serviceChainMap.get(dataPlaneChainName);
						synchronized(chain){
							chain.scaleIndicators.get(dcIndex)[i] = true;
							chain.scaleDownList.get(dcIndex).get(i).clear();
							chain.scaleDownCounter.get(dcIndex)[i] = -1;
						}
						AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
																		dataPlaneChainName,
								                              i, false, dcIndex, hostServer);
						Pending pending = new Pending(1, null);
						this.pendingMap.put(newRequest.getUUID(), pending);
						this.mh.sendTo("vmAllocator", newRequest);
					}
				}
				
				boolean[] controlPlaneArray = recordMap.get(controlPlaneChainName);
				for(int i=0; i<controlPlaneArray.length; i++){
					if(!controlPlaneArray[i]){
						NFVServiceChain chain = this.serviceChainMap.get(controlPlaneChainName);
						synchronized(chain){
							chain.scaleIndicators.get(dcIndex)[i] = true;
							chain.scaleDownList.get(dcIndex).get(i).clear();
							chain.scaleDownCounter.get(dcIndex)[i] = -1;
						}
						AllocateVmRequest newRequest = new AllocateVmRequest(this.getId(),
								                                    controlPlaneChainName,
								                              i, false, dcIndex, hostServer);
						Pending pending = new Pending(1, null);
						this.pendingMap.put(newRequest.getUUID(), pending);
						this.mh.sendTo("vmAllocator", newRequest);	
					}
				}
				
				for(String chainName : hostServer.serviceChainConfigMap.keySet()){
					List<HashMap<String, VmInstance>> list = 
							this.serverVmMap.get(hostServer.hostServerConfig.managementIp).get(chainName);
					for(int j=0; j<list.size(); j++){
						HashMap<String, VmInstance> vmMap = list.get(j);
						for(String mIp : vmMap.keySet()){
							this.serviceChainMap.get(chainName).mask(mIp);
						}
					}
				}
			}
		}
		else{
			HostServer hostServer = this.hostServerMap.get(managementIp);
			for(String chainName : hostServer.serviceChainConfigMap.keySet()){
				List<HashMap<String, VmInstance>> list = 
						this.serverVmMap.get(hostServer.hostServerConfig.managementIp).get(chainName);
				for(int j=0; j<list.size(); j++){
					HashMap<String, VmInstance> vmMap = list.get(j);
					for(String mIp : vmMap.keySet()){
						this.serviceChainMap.get(chainName).unmask(mIp);
					}
				}
			}
		}
	}
	
	private void handleDcLinkStat(DcLinkStat request){
		//Transform the request into a binary graph.
		this.dcLinkGraph.updateDcLinkState(request.getDcSendSpeed(), request.getDcRecvSpeed(), 
										   request.getSize());
	}
	
	public List<HostServer> getPath(HostServer srcServer, HostServer dstServer){
		int src = srcServer.hostServerConfig.dcIndex;
		int dst = dstServer.hostServerConfig.dcIndex;
		List<HostServer> returnList = new ArrayList<HostServer>();
		returnList.add(srcServer);
		
		List<Integer> dcList = this.dcLinkGraph.getPath(src, dst);
		if(dcList.size()>2){
			for(int i=1; i<dcList.size()-1; i++){
				List<String> serverList = this.vmAllocator.getHostServerList(dcList.get(i).intValue());
				returnList.add(this.hostServerMap.get(serverList.get(0)));
			}
		}
		
		returnList.add(dstServer);
		return returnList;
	}
}
