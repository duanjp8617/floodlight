package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import com.google.common.util.concurrent.ListenableFuture;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.nfvtest.message.MessageHub;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;

public class SwitchStatPoller implements Runnable{
	private final MessageHub mh;
	private final IOFSwitchService switchService;
	private final HashMap<String, HostServer> hostServerMap;
	private final HashMap<String, List<String>> serverSwIdListMap;
	public SwitchStatPoller(MessageHub mh, IOFSwitchService switchService, 
			                HashMap<String, HostServer> hostServerMap){
		this.mh = mh;
		this.switchService = switchService;
		this.hostServerMap = hostServerMap;
		this.serverSwIdListMap = new HashMap<String, List<String>>();
		
		for(String managementIp : this.hostServerMap.keySet()){
			HostServer hostServer = this.hostServerMap.get(managementIp);
			for(String chainName : hostServer.serviceChainDpidMap.keySet()){
				if(hostServer.serviceChainDpidMap.get(chainName).size()>0){
					List<String> dpidList = hostServer.serviceChainDpidMap.get(chainName);
					this.serverSwIdListMap.put(managementIp, dpidList);
				}
			}
		}
	}
	
	@Override
	public void run() {
		//Sleep for 
		try{
			Thread.sleep(15000);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		System.out.println("start printing statistics");
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
		}
	}
}
