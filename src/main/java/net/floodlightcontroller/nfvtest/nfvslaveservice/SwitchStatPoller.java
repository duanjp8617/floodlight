package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.HashMap;

import org.projectfloodlight.openflow.protocol.OFStatsType;

import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.web.SwitchResourceBase.REQUESTTYPE;
import net.floodlightcontroller.nfvtest.message.MessageHub;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;

public class SwitchStatPoller implements Runnable{
	private final MessageHub mh;
	private IOFSwitchService switchService;
	private final HashMap<String, HostServer> hostServerMap;
	
	public SwitchStatPoller(MessageHub mh, IOFSwitchService switchService, 
			                HashMap<String, HostServer> hostServerMap){
		this.mh = mh;
		this.switchService = switchService;
		this.hostServerMap = hostServerMap;
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
		
		OFStatsType type = OFStatsType.PORT;
		REQUESTTYPE rType = REQUESTTYPE.OFSTATS;
	}

}
