package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import com.google.common.util.concurrent.ListenableFuture;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.DcLinkStat;
import net.floodlightcontroller.nfvtest.message.MessageHub;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;

public class SwitchStatPoller implements Runnable{
	private final MessageHub mh;
	private final IOFSwitchService switchService;
	private final HashMap<String, HostServer> hostServerMap;
	private final HashMap<String, List<String>> serverSwIdListMap;
	private final HashMap<String, HostServer> dpidHostServerMap;
	public SwitchStatPoller(MessageHub mh, IOFSwitchService switchService, 
			                HashMap<String, HostServer> hostServerMap){
		this.mh = mh;
		this.switchService = switchService;
		this.hostServerMap = hostServerMap;
		this.serverSwIdListMap = new HashMap<String, List<String>>();
		this.dpidHostServerMap = new HashMap<String, HostServer>();
		
		for(String managementIp : this.hostServerMap.keySet()){
			HostServer hostServer = this.hostServerMap.get(managementIp);
			for(String chainName : hostServer.serviceChainDpidMap.keySet()){
				if(hostServer.serviceChainDpidMap.get(chainName).size()>0){
					List<String> dpidList = hostServer.serviceChainDpidMap.get(chainName);
					this.serverSwIdListMap.put(managementIp, dpidList);
					for(int i=0; i<dpidList.size(); i++){
						this.dpidHostServerMap.put(dpidList.get(i), hostServer);
					}
				}
			}
		}
	}
	
	@Override
	public void run() {
		//Sleep for 
		try{
			Thread.sleep(10000);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		String[] mipList = this.hostServerMap.keySet()
	               .toArray(new String[this.hostServerMap.size()]);
		int dcNum = this.hostServerMap.get(mipList[0]).controllerConfig.dcNum;
		
		int[] dcServerNumArray = new int[dcNum];
		for(int i=0; i<dcServerNumArray.length; i++){
			dcServerNumArray[i] = 0;
		}

		for(int i=0; i<mipList.length; i++){
			HostServer hostServer = this.hostServerMap.get(mipList[i]);
			dcServerNumArray[hostServer.hostServerConfig.dcIndex]+=1;
		}
		
		HashMap<String, Integer> serverIndexMap = new HashMap<String, Integer>();
		int[] tmp = new int[dcNum];
		for(int i=0; i<tmp.length; i++){
			tmp[i] = 0;
		}
		for(int i=0; i<mipList.length; i++){
			HostServer hostServer = this.hostServerMap.get(mipList[i]);
			int dcIndex = hostServer.hostServerConfig.dcIndex;
			int base = 0;
			for(int j=0; j<dcIndex; j++){
				base += dcServerNumArray[j];
			}
			serverIndexMap.put(mipList[i], new Integer(base+tmp[dcIndex]));
			tmp[dcIndex]+=1;
		}
		
		HashMap<String, ListenableFuture<?>> dpidFutureMap = 
							new HashMap<String, ListenableFuture<?>>();
		HashMap<String, List<OFPortStatsReply>> dpidReplyMap = 
							new HashMap<String, List<OFPortStatsReply>>();
		
		long[][] oldSendBytes = new long[mipList.length][mipList.length];
		long[][] oldRecvBytes = new long[mipList.length][mipList.length];
		
		long[][] newSendBytes = new long[mipList.length][mipList.length];
		long[][] newRecvBytes = new long[mipList.length][mipList.length];
		
		float[][] serverSend = new float[mipList.length][mipList.length];
		float[][] serverRecv = new float[mipList.length][mipList.length];
		
		float[][] dcSend = new float[dcNum][dcNum];
		float[][] dcRecv = new float[dcNum][dcNum];
		
		for(int i=0; i<mipList.length; i++){
			for(int j=0; j<mipList.length; j++){
				oldSendBytes[i][j] = 0;
				oldRecvBytes[i][j] = 0;
				newSendBytes[i][j] = 0;
				newRecvBytes[i][j] = 0;
				serverSend[i][j]=0;
				serverRecv[i][j]=0;
			}
		}
		
		for(int i=0; i<dcNum; i++){
			for(int j=0; j<dcNum; j++){
				dcSend[i][j]=0;
				dcRecv[i][j]=0;
			}
		}
		
		for(int i=0; i<mipList.length; i++){
			
			List<String> dpidList = this.serverSwIdListMap.get(mipList[i]);
			
			for(int j=0; j<dpidList.size(); j++){
				IOFSwitch sw = switchService.getSwitch(DatapathId.of(dpidList.get(j)));
				ListenableFuture<?> future = null;

			
				OFStatsRequest<?> req  = sw.getOFFactory().buildPortStatsRequest()
										   .setPortNo(OFPort.ANY)
					                       .build();
			
				try {
					if (req != null) {
						future = sw.writeStatsRequest(req);
					}
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("failed to retrieve the stat from switch");
				}
				
				dpidFutureMap.put(dpidList.get(j), future);
			}
		}
		
		for(int i=0; i<mipList.length; i++){
			
			List<String> dpidList = this.serverSwIdListMap.get(mipList[i]);
			
			for(int j=0; j<dpidList.size(); j++){
				ListenableFuture<?> future = dpidFutureMap.get(dpidList.get(j));
				List<OFPortStatsReply> values = null;
				try {
					values = (List<OFPortStatsReply>) future.get(10, TimeUnit.SECONDS);
				}catch (Exception e) {
					e.printStackTrace();
					System.out.println("failed to retrieve the stat from switch");
				}
				
				dpidReplyMap.put(dpidList.get(j), values);
			}
		}
		
		long startTime = System.currentTimeMillis();
		
		for(String dpid : dpidReplyMap.keySet()){
			HostServer rowServer = this.dpidHostServerMap.get(dpid);
			String rowServerIp = rowServer.hostServerConfig.managementIp;
			
			
			List<OFPortStatsReply> values = dpidReplyMap.get(dpid);
			for(int i=0; i<values.size(); i++){
				OFPortStatsReply stat = values.get(i);
				for(OFPortStatsEntry entry : stat.getEntries()) {
					Integer port = new Integer(entry.getPortNo().toString());
					if(rowServer.portTunnelMap.containsKey(port)){
						String colServerIp = rowServer.portTunnelMap.get(port);
						
						int row = serverIndexMap.get(rowServerIp);
						int col = serverIndexMap.get(colServerIp);
						
						long sendBytes = entry.getTxBytes().getValue();
						long recvBytes = entry.getRxBytes().getValue();
						
						oldSendBytes[row][col] += sendBytes;
						oldRecvBytes[row][col] += recvBytes;
					}
				}
			}
		}
		
		try{
			Thread.sleep(1000);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		while(true){
			dpidFutureMap.clear();
			dpidReplyMap.clear();
			
			for(int i=0; i<mipList.length; i++){
				
				List<String> dpidList = this.serverSwIdListMap.get(mipList[i]);
				
				for(int j=0; j<dpidList.size(); j++){
					IOFSwitch sw = switchService.getSwitch(DatapathId.of(dpidList.get(j)));
					ListenableFuture<?> future = null;

				
					OFStatsRequest<?> req  = sw.getOFFactory().buildPortStatsRequest()
											   .setPortNo(OFPort.ANY)
						                       .build();
				
					try {
						if (req != null) {
							future = sw.writeStatsRequest(req);
						}
					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("failed to retrieve the stat from switch");
					}
					
					dpidFutureMap.put(dpidList.get(j), future);
				}
			}
			
			for(int i=0; i<mipList.length; i++){
				
				List<String> dpidList = this.serverSwIdListMap.get(mipList[i]);
				
				for(int j=0; j<dpidList.size(); j++){
					ListenableFuture<?> future = dpidFutureMap.get(dpidList.get(j));
					List<OFPortStatsReply> values = null;
					try {
						values = (List<OFPortStatsReply>) future.get(10, TimeUnit.SECONDS);
					}catch (Exception e) {
						e.printStackTrace();
						System.out.println("failed to retrieve the stat from switch");
					}
					
					dpidReplyMap.put(dpidList.get(j), values);
				}
			}
			
			long oldStartTime = startTime;
			startTime = System.currentTimeMillis();
			
			for(String dpid : dpidReplyMap.keySet()){
				HostServer rowServer = this.dpidHostServerMap.get(dpid);
				String rowServerIp = rowServer.hostServerConfig.managementIp;
				
				List<OFPortStatsReply> values = dpidReplyMap.get(dpid);
				for(int i=0; i<values.size(); i++){
					OFPortStatsReply stat = values.get(i);
					for(OFPortStatsEntry entry : stat.getEntries()) {
						Integer port = new Integer(entry.getPortNo().toString());
						if(rowServer.portTunnelMap.containsKey(port)){
							String colServerIp = rowServer.portTunnelMap.get(port);
							
							int row = serverIndexMap.get(rowServerIp);
							int col = serverIndexMap.get(colServerIp);
							
							long sendBytes = entry.getTxBytes().getValue();
							long recvBytes = entry.getRxBytes().getValue();
							
							newSendBytes[row][col] += sendBytes;
							newRecvBytes[row][col] += recvBytes;
						}
					}
				}
			}	
			
			for(int i=0; i<mipList.length; i++){
				for(int j=0; j<mipList.length; j++){
					long sendBytes = newSendBytes[i][j] - oldSendBytes[i][j];
					oldSendBytes[i][j] = newSendBytes[i][j];
					newSendBytes[i][j] = 0;
					//speed in kbits/s
					float sendSpeed = ((float)sendBytes)/((float)(startTime-oldStartTime))*1000/1024*8;
					serverSend[i][j] = sendSpeed;
					
					long recvBytes = newRecvBytes[i][j] - oldRecvBytes[i][j];
					oldRecvBytes[i][j] = newRecvBytes[i][j];
					newRecvBytes[i][j] = 0;
					//speed in kbits/s
					float recvSpeed = ((float)recvBytes)/((float)(startTime-oldStartTime))*1000/1024*8;
					serverRecv[i][j] = recvSpeed;
					
					HostServer rowServer = this.hostServerMap.get(mipList[i]);
					int rowServerDcIndex = rowServer.hostServerConfig.dcIndex;
					HostServer colServer = this.hostServerMap.get(mipList[j]);
					int colServerDcIndex = colServer.hostServerConfig.dcIndex;
					
					if(rowServerDcIndex == colServerDcIndex){
						continue;
					}
					else{
						dcSend[rowServerDcIndex][colServerDcIndex]+=sendSpeed;
						dcRecv[rowServerDcIndex][colServerDcIndex]+=recvSpeed;
					}
				}
			}
			
			float[][] dcSendCopy = new float[dcNum][dcNum];
			float[][] dcRecvCopy = new float[dcNum][dcNum];
			
			for(int i=0; i<dcNum; i++){
				for(int j=0; j<dcNum; j++){
					dcSendCopy[i][j] = dcSend[i][j];
					dcRecvCopy[i][j] = dcRecv[i][j];
				}
			}
			
			DcLinkStat dcLinkStat = new DcLinkStat("hehehe", dcNum, dcSendCopy, dcRecvCopy);
			this.mh.sendTo("chainHandler", dcLinkStat);
			
			for(int i=0; i<dcNum; i++){
				for(int j=0; j<dcNum; j++){
					dcSend[i][j]=0;
					dcRecv[i][j]=0;
				}
			}
			
			try{
				Thread.sleep(1000);
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
		/*System.out.println("start printing statistics");
		while(true){
			for(String managementIp : this.serverSwIdListMap.keySet()){
				List<String> dpidList = this.serverSwIdListMap.get(managementIp);
			
				for(int i=0; i<dpidList.size(); i++){
					IOFSwitch sw = switchService.getSwitch(DatapathId.of(dpidList.get(i)));
					ListenableFuture<?> future;
					List<OFPortStatsReply> values = null;
				
					OFStatsRequest<?> req = null;
					req = sw.getOFFactory().buildPortStatsRequest()
						.setPortNo(OFPort.ANY)
						.build();
				
					try {
						if (req != null) {
							future = sw.writeStatsRequest(req);
							values = (List<OFPortStatsReply>) future.get(10, TimeUnit.SECONDS);
						}
					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("failed to retrieve the stat from switch");
					}
				
					for(int j=0; j<values.size(); j++){
						OFPortStatsReply stat = values.get(j);
						for(OFPortStatsEntry entry : stat.getEntries()) {
							System.out.println("portNumber "+entry.getPortNo().toString());
							System.out.println("receivePackets "+entry.getRxPackets().getValue());
							System.out.println("transmitPackets "+entry.getTxPackets().getValue());
							System.out.println("receiveBytes "+entry.getRxBytes().getValue());
							System.out.println("transmitBytes "+entry.getTxBytes().getValue());
							System.out.println("receiveDropped "+entry.getRxDropped().getValue());
							System.out.println("transmitDropped "+entry.getTxDropped().getValue());
							System.out.println("receiveErrors "+entry.getRxErrors().getValue());
							System.out.println("transmitErrors "+entry.getTxErrors().getValue());
							System.out.println("receiveFrameErrors "+entry.getRxFrameErr().getValue());
							System.out.println("receiveOverrunErrors "+entry.getRxOverErr().getValue());
							System.out.println("receiveCRCErrors "+entry.getRxCrcErr().getValue());
							System.out.println("collisions "+entry.getCollisions().getValue());
						}
					}
				}
			}
			try{
				Thread.sleep(2000);
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}*/
	}
}
