package net.floodlightcontroller.nfvtest.nfvslaveservice;
import java.util.concurrent.LinkedBlockingQueue;

import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.nfvutils.HostAgent;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer.*;
import net.floodlightcontroller.nfvtest.message.Pending;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

import org.projectfloodlight.openflow.types.DatapathId;


public class VmAllocator extends MessageProcessor {
	private final ArrayList<HostServer> hostServerList;
	private final HashMap<UUID, Pending> pendingMap;
	private int vni;
	public final HashMap<DatapathId, HostServer> dpidHostServerMap;
	public final HashMap<DatapathId, Integer> dpidStageIndexMap;
	
	private final HashMap<Integer, TreeMap<TreeMapKey, HostServer>> serverLoadMap;
	private final HashMap<String, HostServer> hostServerMap;
	
	//private final HashMap<String, HashMap<String, List<HashMap<String, VmInstance>>>> serverVmMap;
	
	public class TreeMapKey implements Comparable<TreeMapKey>{
		public final Integer vmNum;
		public final String managementIp;
		
		public TreeMapKey(Integer vmNum, String managementIp){
			this.vmNum = vmNum;
			this.managementIp = managementIp;
		}

		@Override
		public int compareTo(TreeMapKey o) {
			// TODO Auto-generated method stub
			if(this.vmNum.compareTo(o.vmNum)==0){
				return this.managementIp.compareTo(o.managementIp);
			}
			else{
				return this.vmNum.compareTo(o.vmNum);
			}
		}
	}

	public VmAllocator(String id){
		this.id = id;
		this.queue = new LinkedBlockingQueue<Message>();
		this.hostServerList = new ArrayList<HostServer>();
		this.pendingMap = new HashMap<UUID, Pending>();
		this.vni=100;
		this.dpidHostServerMap = new HashMap<DatapathId, HostServer>();
		this.dpidStageIndexMap = new HashMap<DatapathId, Integer>();
		
		this.hostServerMap = new HashMap<String, HostServer>();
		this.serverLoadMap = new HashMap<Integer, TreeMap<TreeMapKey, HostServer>>();
		
		//this.serverVmMap = new HashMap<String, HashMap<String, List<HashMap<String, VmInstance>>>>();
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
	}
	
	private synchronized void allocateVm(AllocateVmRequest originalRequest){
		HostServer ignoredServer = null;
		TreeMapKey ignoredServerKey = null;
		
		int dcIndex = originalRequest.getDcIndex();
		TreeMap<TreeMapKey, HostServer> serverLoadTMap = this.serverLoadMap.get(new Integer(dcIndex));
		if(originalRequest.getIgnoredServer()!=null){
			for(TreeMapKey key : serverLoadTMap.keySet()){
				if(serverLoadTMap.get(key).hostServerConfig.managementIp
						.equals(originalRequest.getIgnoredServer().hostServerConfig.managementIp)){
					ignoredServerKey = key;
					break;
				}
			}
			ignoredServer = serverLoadTMap.remove(ignoredServerKey);
		}
		
		TreeMapKey currentKey = serverLoadTMap.lastKey();
		HostServer hostServer = serverLoadTMap.get(currentKey);
		
		VmInstance vmInstance = 
				hostServer.allocateVmInstance(originalRequest.getChainName(), originalRequest.getStageIndex(),
						                      originalRequest.getIsBufferNode());
		
		while(vmInstance==null){
			if(currentKey.compareTo(serverLoadTMap.firstKey()) == 0){
				break;
			}
			
			currentKey = serverLoadTMap.lowerKey(currentKey);
			hostServer = serverLoadTMap.get(currentKey);
			
			vmInstance = 
				hostServer.allocateVmInstance(originalRequest.getChainName(), originalRequest.getStageIndex(),
							                  originalRequest.getIsBufferNode());
		}
		
		if(vmInstance!=null){
			hostServer = serverLoadTMap.remove(currentKey);
			TreeMapKey newKey = new TreeMapKey(new Integer(currentKey.vmNum.intValue()+1), 
											   hostServer.hostServerConfig.managementIp);
			serverLoadTMap.put(newKey, hostServer);
			
			CreateVmRequest newRequest = new CreateVmRequest(this.getId(), vmInstance);
			Pending pending = new Pending(1, originalRequest);
			this.pendingMap.put(newRequest.getUUID(), pending);
			this.mh.sendTo("vmWorker", newRequest);
		}
		else{
			//do something to notify the sender of out of resource.
			AllocateVmReply originalReply = new AllocateVmReply(this.getId(), null, originalRequest);
			this.mh.sendTo(originalRequest.getSourceId(), originalReply);
		}
		
		if(originalRequest.getIgnoredServer()!=null){
			serverLoadTMap.put(ignoredServerKey, ignoredServer);
		}
	}
	
	private void deallocateVm(DeallocateVmRequest originalRequest){
		DestroyVmRequest newRequest = new DestroyVmRequest(this.getId(), originalRequest.getVmInstance());
		Pending pending = new Pending(1, originalRequest);
		this.pendingMap.put(newRequest.getUUID(), pending);
		
		/*VmInstance vmInstance = originalRequest.getVmInstance();
		String serverIp = vmInstance.hostServerConfig.managementIp;
		String chainName = vmInstance.serviceChainConfig.name;
		int stageIndex = vmInstance.stageIndex;
		this.serverVmMap.get(serverIp).get(chainName)
		                .get(stageIndex).remove(vmInstance.managementIp);*/
		
		this.mh.sendTo("vmWorker", newRequest);
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
			
			/*VmInstance vmInstance = newReply.getRequest().getVmInstance();
			String serverIp = vmInstance.hostServerConfig.managementIp;
			String chainName = vmInstance.serviceChainConfig.name;
			int stageIndex = vmInstance.stageIndex;
			this.serverVmMap.get(serverIp).get(chainName)
			                .get(stageIndex).put(vmInstance.managementIp, vmInstance);*/
			
			this.mh.sendTo(originalReply.getAllocateVmRequest().getSourceId(), originalReply);
		}
		this.pendingMap.remove(newRequest.getUUID());
	}
	
	private synchronized void handleDestroyVmReply(DestroyVmReply newReply){
		DestroyVmRequest newRequest = newReply.getRequest();
		Pending pending = this.pendingMap.get(newRequest.getUUID());
		pending.addReply(newReply);
		
		if(newReply.getSuccessful()){
			boolean returnVal = 
					newRequest.getVmInstance().hostServer.deallocateVmInstance(newRequest.getVmInstance());
			if(returnVal){
				VmInstance vmInstance = newRequest.getVmInstance();
				HostServer hostServer = vmInstance.hostServer;
				int dcIndex = vmInstance.hostServerConfig.dcIndex;
				TreeMap<TreeMapKey, HostServer> serverLoadTMap = 
						this.serverLoadMap.get(new Integer(dcIndex));
				TreeMapKey targetKey = null;
				
				for(TreeMapKey key : serverLoadTMap.keySet()){
					if(serverLoadTMap.get(key).hostServerConfig.managementIp
							.equals(hostServer.hostServerConfig.managementIp)){
						targetKey = key;
						break;
					}
				}
				
				hostServer = serverLoadTMap.remove(targetKey);
				TreeMapKey newKey = new TreeMapKey(new Integer(targetKey.vmNum.intValue()-1), 
						   hostServer.hostServerConfig.managementIp);
				serverLoadTMap.put(newKey, hostServer);
			}
		}
	}
	
	private void addHostServer(AddHostServerRequest request){
		if(this.hostServerList.size()==0){
			this.hostServerList.add(request.getHostServer());
		}
		else{
			try{
				HostServer serverToAdd = request.getHostServer();
				HostAgent newAgent = new HostAgent(serverToAdd.hostServerConfig);
				newAgent.connect();
				int returnVal = 0;
				for(int i=0; i<this.hostServerList.size(); i++){
					HostAgent oldAgent = new HostAgent(this.hostServerList.get(i).hostServerConfig);
					oldAgent.connect();
					
					returnVal=oldAgent.createTunnelTo(this.hostServerList.get(i), serverToAdd, this.vni);
					returnVal=newAgent.createTunnelTo(serverToAdd, this.hostServerList.get(i), this.vni);
					if(returnVal>0){
						this.vni = returnVal;
					}
					
					oldAgent.createRouteTo(this.hostServerList.get(i), serverToAdd);
					newAgent.createRouteTo(serverToAdd, this.hostServerList.get(i));
					
					oldAgent.disconnect();
				}
				newAgent.disconnect();
			}
			catch(Exception e){
				e.printStackTrace();
			}
			this.hostServerList.add(request.getHostServer());
		}
		HostServer hostServer = request.getHostServer();
		for(String chainName : hostServer.serviceChainDpidMap.keySet()){
			List<String> dpidList = hostServer.serviceChainDpidMap.get(chainName);
			for(int i=0; i<dpidList.size(); i++){
				this.dpidHostServerMap.put(DatapathId.of(dpidList.get(i)), hostServer);
				this.dpidStageIndexMap.put(DatapathId.of(dpidList.get(i)), new Integer(i));
			}
		}
		this.hostServerMap.put(hostServer.hostServerConfig.managementIp, hostServer);
		int dcIndex = hostServer.hostServerConfig.dcIndex;
		if(!this.serverLoadMap.containsKey(new Integer(dcIndex))){
			this.serverLoadMap.put(new Integer(dcIndex), new TreeMap<TreeMapKey, HostServer>());
			TreeMapKey key = new TreeMapKey(new Integer(0), hostServer.hostServerConfig.managementIp);
			this.serverLoadMap.get(new Integer(dcIndex)).put(key,hostServer);
		}
		else{
			TreeMapKey key = new TreeMapKey(new Integer(0), hostServer.hostServerConfig.managementIp);
			this.serverLoadMap.get(new Integer(dcIndex)).put(key,hostServer);
		}
		
		/*HashMap<String, List<HashMap<String, VmInstance>>> vmMap = 
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
		this.serverVmMap.put(hostServer.hostServerConfig.managementIp, vmMap);*/
	}
	
	public synchronized List<String> getHostServerList(int dcIndex){
		TreeMap<TreeMapKey, HostServer> serverLoadTMap = this.serverLoadMap.get(new Integer(dcIndex));
		List<String> returnList = new ArrayList<String>();
		TreeMapKey key = serverLoadTMap.firstKey();
		while(key!=null){
			returnList.add(serverLoadTMap.get(key).hostServerConfig.managementIp);
			key = serverLoadTMap.higherKey(key);
		}
		return returnList;
	}
	
	//public synchronized int[] 
	
	/*public synchronized String getScaleDownNode(String chainName, int dcIndex, int stageIndex,
			                            HashMap<String, Integer> errorMap){
		TreeMap<TreeMapKey, HostServer> serverLoadTMap = this.serverLoadMap.get(new Integer(dcIndex));
		
		TreeMapKey currentKey = serverLoadTMap.firstKey();
		HostServer hostServer = serverLoadTMap.get(currentKey);
		String serverIp = hostServer.hostServerConfig.managementIp;
		
		String mIp = this.haveNode(serverIp, chainName, stageIndex, errorMap);
		
		while(mIp==null){
			currentKey = serverLoadTMap.higherKey(currentKey);
			hostServer = serverLoadTMap.get(currentKey);
			serverIp = hostServer.hostServerConfig.managementIp;
			
			mIp = this.haveNode(serverIp, chainName, stageIndex, errorMap);
			
			if(currentKey.compareTo(serverLoadTMap.lastKey())==0){
				break;
			}
		}
		return mIp;
	}
	
	//on server serverIp, on service chain chainName, do we have a node on stage stageIndex
	//that is not in errorMap. If so return the managementIp of that node, otherwise return 0
	private String haveNode(String serverIp, String chainName, int stageIndex,
							HashMap<String, Integer> errorMap){
		HashMap<String, VmInstance> vmMap = this.serverVmMap.get(serverIp).get(chainName).get(stageIndex);
		String[] vmIps = vmMap.keySet().toArray(new String[vmMap.size()]);
		
		for(int i=0; i<vmIps.length; i++){
			if(!errorMap.containsKey(vmIps[i])){
				return vmIps[i];
			}
		}
		
		return null;
	}*/
}
