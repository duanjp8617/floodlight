package net.floodlightcontroller.nfvtest.nfvcorestructure;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
 
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
 
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import net.floodlightcontroller.core.IFloodlightProviderService;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Set;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;

import java.util.Set;
import java.util.Collection;

import net.floodlightcontroller.learningswitch.LearningSwitch;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.InitServiceChainRequset;
import net.floodlightcontroller.nfvtest.message.MessageHub;
import net.floodlightcontroller.nfvtest.localcontroller.LocalController;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.AddHostServerRequest;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.AllocateVmRequest;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.HostInitializationRequest;
import net.floodlightcontroller.nfvtest.nfvslaveservice.DNSUpdator;
import net.floodlightcontroller.nfvtest.nfvslaveservice.ServiceChainHandler;
import net.floodlightcontroller.nfvtest.nfvslaveservice.SubscriberConnector;
import net.floodlightcontroller.nfvtest.nfvslaveservice.VmAllocator;
import net.floodlightcontroller.nfvtest.nfvslaveservice.VmWorker;
import net.floodlightcontroller.nfvtest.nfvutils.HostServer;
import net.floodlightcontroller.nfvtest.nfvutils.IpAddressAllocator;
import net.floodlightcontroller.nfvtest.nfvutils.MacAddressAllocator;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.ControllerConfig;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.HostServerConfig;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.ServiceChainConfig;
import net.floodlightcontroller.nfvtest.nfvutils.GlobalConfig.StageVmInfo;
import net.floodlightcontroller.nfvtest.nfvutils.FlowTuple;
import net.floodlightcontroller.nfvtest.nfvutils.Pair;
import net.floodlightcontroller.nfvtest.nfvutils.RouteTuple;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

import net.floodlightcontroller.nfvtest.nfvslaveservice.NFVZmqPoller;


 
public class NFVTest implements IOFMessageListener, IFloodlightModule {
 
	protected IFloodlightProviderService floodlightProvider;
	protected Set<Long> macAddresses;
	protected static Logger logger;
    protected IOFSwitchService switchService;
    
	private ControllerConfig controllerConfig;
	private HostServerConfig hostServerConfig;
	private HostServerConfig hostServerConfig1;
	private MacAddressAllocator macAllocator;
	private HostServer hostServer;
	private HostServer hostServer1;
	private IpAddressAllocator ipAllocator;
	private HashMap<DatapathId, HostServer> dpidHostServerMap;
	
	private ServiceChainConfig dpServiceChainConfig;
	private ServiceChainConfig cpServiceChainConfig;
	private NFVServiceChain dpServiceChain;
	private NFVServiceChain cpServiceChain;
	
	private LocalController localController;
	
	private HashMap<FlowTuple, Integer> flowMap;
	private HashMap<RouteTuple, String> routeMap;
	
	@Override
	public String getName() {
	    return NFVTest.class.getSimpleName();
	}
 
    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        // TODO Auto-generated method stub
        return false;
    }
 
    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        // TODO Auto-generated method stub
        return (type.equals(OFType.PACKET_IN) && name.equals("forwarding"));
    }
 
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // TODO Auto-generated method stub
        return null;
    }
 
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        // TODO Auto-generated method stub
        return null;
    }
 
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
            new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IOFSwitchService.class);
        return l;
    }
 
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        macAddresses = new ConcurrentSkipListSet<Long>();
        logger = LoggerFactory.getLogger(NFVTest.class);
        
        logger.info("start testing network xml");
        //TestHostServer testHostServer = new TestHostServer();
        //testHostServer.testVmAllocator();
		this.controllerConfig = 
				new ControllerConfig("202.45.128.151", "/home/net/base-env", "basexml.xml", "networkxml.xml");
		
		this.hostServerConfig = 
				new HostServerConfig("202.45.128.149", "1.1.1.2", "2.2.2.2", 1, 32*1024, 100*1024, 1,
						             "xx", "xx", "/home/net/nfvenv");
		
		this.hostServerConfig1 = 
				new HostServerConfig("202.45.128.151", "1.1.1.2", "2.2.2.2", 1, 32*1024, 100*1024, 1,
						             "xx", "xx", "/home/net/nfvenv");
		
		//create service chain configuration for control plane
		StageVmInfo bonoInfo = new StageVmInfo(1,2*1024,2*1024,"bono.img");
		StageVmInfo sproutInfo = new StageVmInfo(1,2*1024,2*1024,"sprout.img");
		ArrayList<StageVmInfo> cpList = new ArrayList<StageVmInfo>();
		cpList.add(bonoInfo);
		cpList.add(sproutInfo);
		this.cpServiceChainConfig = new ServiceChainConfig("CONTROL", 2, cpList);
		
		//create data plane service chain configuration
		StageVmInfo firewallInfo = new StageVmInfo(1, 2*1024, 2*1024, "firewall.img");
		StageVmInfo ipsInfo = new StageVmInfo(1, 2*1024, 2*1024, "ips.img");
		StageVmInfo transcoderInfo = new StageVmInfo(1, 2*1024, 2*1024, "transcoder.img");
		ArrayList<StageVmInfo> dpList = new ArrayList<StageVmInfo>();
		dpList.add(firewallInfo);
		dpList.add(ipsInfo);
		dpList.add(transcoderInfo);
		this.dpServiceChainConfig = new ServiceChainConfig("DATA", 2, dpList);
		
		byte[] prefix = new byte[3];
		prefix[0] = 0x52;
		prefix[1] = 0x54;
		prefix[2] = 0x00;
		macAllocator = new MacAddressAllocator(prefix);
		ipAllocator = new IpAddressAllocator(192,168,64);
			
		HashMap<String, ServiceChainConfig> map = new HashMap<String, ServiceChainConfig>();
		map.put(this.cpServiceChainConfig.name, this.cpServiceChainConfig);
		map.put(this.dpServiceChainConfig.name, this.dpServiceChainConfig);
		
		hostServer = new HostServer(this.controllerConfig, this.hostServerConfig, map, this.macAllocator,
										this.ipAllocator, "192.168.1.1", "192.168.1.2");
		hostServer1 = new HostServer(this.controllerConfig, this.hostServerConfig1, map, this.macAllocator,
										this.ipAllocator, "192.168.1.1", "192.168.1.2");
		
		this.flowMap = new HashMap<FlowTuple, Integer>();
		this.routeMap = new HashMap<RouteTuple, String>();
		
		this.cpServiceChain = new NFVServiceChain(this.cpServiceChainConfig);
		this.dpServiceChain = new NFVServiceChain(this.dpServiceChainConfig);
		
		MessageHub mh = new MessageHub();
		
		VmWorker vmWorker = new VmWorker("vmWorker");
		vmWorker.registerWithMessageHub(mh);
		
		VmAllocator vmAllocator = new VmAllocator("vmAllocator");
		vmAllocator.registerWithMessageHub(mh);
		
		Context zmqContext = ZMQ.context(1);
		SubscriberConnector subscriberConnector = new SubscriberConnector("subscriberConnector",
																			zmqContext);
		subscriberConnector.registerWithMessageHub(mh);
		
		//DNSUpdator dnsUpdator = new DNSUpdator("dnsUpdator", "192.168.126.123", "7773", zmqContext);
		//dnsUpdator.registerWithMessageHub(mh);
		//dnsUpdator.connect();
		
		ServiceChainHandler chainHandler = new ServiceChainHandler("chainHandler");
		chainHandler.registerWithMessageHub(mh);
		chainHandler.startPollerThread();
		chainHandler.addServiceChain(this.cpServiceChain);
		chainHandler.addServiceChain(this.dpServiceChain);
		
		mh.startProcessors();
		
		HostInitializationRequest m = new HostInitializationRequest("hehe",this.hostServer);
		mh.sendTo("vmWorker", m);
		try{
			Thread.sleep(5000);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		HostInitializationRequest m1 = new HostInitializationRequest("hehe",this.hostServer1);
		mh.sendTo("vmWorker", m1);
		try{
			Thread.sleep(5000);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		AddHostServerRequest m2 = new AddHostServerRequest("hehe", this.hostServer);
		mh.sendTo("vmAllocator", m2);
		try{
			Thread.sleep(200);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		AddHostServerRequest m3 = new AddHostServerRequest("hehe", this.hostServer1);
		mh.sendTo("vmAllocator", m3);
		try{
			Thread.sleep(200);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		dpidHostServerMap = vmAllocator.dpidHostServerMap;
		
		int c[] = {100, 30, 80};
		localController = new LocalController("127.0.0.1", 5555, 5556, 5557, 5558, "127.0.0.1", true, 2000, c, 
				this.dpServiceChain, this.cpServiceChain, mh, zmqContext);
		Thread lcThread = new Thread(localController);
		lcThread.start();
       
    }
 
    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
        floodlightProvider.addOFMessageListener(OFType.ERROR, this);
    }
    
    private net.floodlightcontroller.core.IListener.Command processPktIn(IOFSwitch sw, OFMessage msg, FloodlightContext cntx){
    	Ethernet eth =
                IFloodlightProviderService.bcStore.get(cntx,
                                            IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        IPacket pkt = eth.getPayload(); 
        OFPacketIn pi = (OFPacketIn)msg;
        if (eth.isBroadcast() || eth.isMulticast()) {
            if (pkt instanceof ARP) {   
            	MacAddress srcMac = eth.getSourceMACAddress();
            	String switchDpid = this.dpServiceChain.getDpidForMac(srcMac.toString());
            	
            	if(switchDpid != null){
            		if(!this.dpServiceChain.macOnRearSwitch(srcMac.toString())){
            			handleArp(eth, sw, pi);
            			return Command.STOP;
            		}
            		else{
            			return Command.CONTINUE;
            		}
            	}
            	else{
            		return Command.CONTINUE;
            	}
            }
        } 
        
        if(pkt instanceof IPv4){
        	IPv4 ip_pkt = (IPv4)pkt;
       	 	int destIpAddress = ip_pkt.getDestinationAddress().getInt();
       	 	if(destIpAddress == IPv4Address.of("192.168.57.51").getInt()){
       	 		if(sw.getId().getLong() == DatapathId.of(this.dpServiceChain.getEntryDpid()).getLong()){
       	 			serviceChainLoadBalancing(sw, cntx, pi.getMatch().get(MatchField.IN_PORT));
       	 			return Command.STOP;
       	 		}
       	 	}
        }
        
        return Command.CONTINUE;
    }
    
    private void handleArp(Ethernet eth, IOFSwitch sw, OFPacketIn pi){
    	ARP arpRequest = (ARP) eth.getPayload();
        MacAddress srcMac = eth.getSourceMACAddress();

        String switchDpid = this.dpServiceChain.getDpidForMac(srcMac.toString());
        if(switchDpid != null){
        	IPacket arpReply = new Ethernet()
            .setSourceMACAddress(MacAddress.of(switchDpid))
            .setDestinationMACAddress(eth.getSourceMACAddress())
            .setEtherType(EthType.ARP)
            .setVlanID(eth.getVlanID())
            .setPriorityCode(eth.getPriorityCode())
            .setPayload(
                new ARP()
                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                .setProtocolType(ARP.PROTO_TYPE_IP)
                .setHardwareAddressLength((byte) 6)
                .setProtocolAddressLength((byte) 4)
                .setOpCode(ARP.OP_REPLY)
                .setSenderHardwareAddress(MacAddress.of(switchDpid))
                .setSenderProtocolAddress(arpRequest.getTargetProtocolAddress())
                .setTargetHardwareAddress(eth.getSourceMACAddress())
                .setTargetProtocolAddress(arpRequest.getSenderProtocolAddress()));
        	
        	OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
        	List<OFAction> actions = new ArrayList<OFAction>();
        	OFPort outPort = pi.getMatch().get(MatchField.IN_PORT);
            actions.add(sw.getOFFactory().actions().buildOutput().setPort(outPort).setMaxLen(Integer.MAX_VALUE).build());
            pob.setActions(actions);
            pob.setBufferId(OFBufferId.NO_BUFFER);
            pob.setInPort(OFPort.ANY);
            byte[] packetData = arpReply.serialize();
            pob.setData(packetData);
            sw.write(pob.build());
        }
    }
    
    private void serviceChainLoadBalancing(IOFSwitch sw, FloodlightContext cntx, OFPort initialInPort){
    	synchronized(this.dpServiceChain){
        	Ethernet eth =
                    IFloodlightProviderService.bcStore.get(cntx,
                                                IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        	IPv4 ip_pkt = (IPv4)eth.getPayload();
        	IPv4Address srcIp = ip_pkt.getSourceAddress();
        	IPv4Address dstIp = ip_pkt.getDestinationAddress();
  
        	TransportPort srcPort = null;
        	TransportPort dstPort = null;
        	IpProtocol transportProtocol = ip_pkt.getProtocol();
        	if(transportProtocol.equals(IpProtocol.TCP)){
        		TCP tcp_pkt = (TCP)ip_pkt.getPayload();
        		srcPort = tcp_pkt.getSourcePort();
        		dstPort = tcp_pkt.getDestinationPort();
        	}
        	else if(ip_pkt.getProtocol().equals(IpProtocol.UDP)){
        		UDP udp_pkt = (UDP)ip_pkt.getPayload();
        		srcPort = udp_pkt.getSourcePort();
        		dstPort = udp_pkt.getDestinationPort();
        	}
        	else {
        		return;
        	}
        	
        	FlowTuple tuple = new FlowTuple(srcIp.getInt(), dstIp.getInt(), 
        				transportProtocol.equals(IpProtocol.TCP)?FlowTuple.TCP:FlowTuple.UDP,
        						srcPort.getPort(), dstPort.getPort());
        	if(this.flowMap.containsKey(tuple)){
        		return;
        	}
        	else{
        		this.flowMap.put(tuple, 1);
        	}
 
        	OFPort inPort = initialInPort;
        	
    		List<NFVNode> routeList = this.dpServiceChain.forwardRoute();
    		IOFSwitch hitSwitch = sw;
    		//here I need to know the the HostServer class that hitSwitch is on.
    		HostServer localHostServer = this.dpidHostServerMap.get(hitSwitch.getId());
    		
    		for(int i=0; i<routeList.size(); i++){
    			NFVNode currentNode = routeList.get(i);
    			IOFSwitch nodeSwitch = this.switchService.getSwitch(DatapathId.of(currentNode.getBridgeDpid(0)));
    			//create flow rules to route the flow from hitSwitch to nodeSwitch.
    			
    			if(hitSwitch.getId().getLong() == nodeSwitch.getId().getLong()){
    				Match flowMatch = createMatch(hitSwitch, inPort, srcIp, dstIp,
    											  transportProtocol, srcPort, dstPort);
    				OFFlowMod flowMod = createFlowMod(hitSwitch, flowMatch, 
    						                          MacAddress.of(currentNode.getMacAddress(0)),
    												  OFPort.of(currentNode.getPort(0)));
    				hitSwitch.write(flowMod);
    				
    				RouteTuple routeTuple = new RouteTuple(srcIp.getInt(), dstIp.getInt(), 
    			   transportProtocol.equals(IpProtocol.TCP)?RouteTuple.TCP:RouteTuple.UDP,
    								                 srcPort.getPort(), dstPort.getPort(), 
    								DatapathId.of(currentNode.getBridgeDpid(0)).getLong());
    				
    				this.routeMap.put(routeTuple, currentNode.getManagementIp());
    			}
    			else{
    				//temporarily ignore this condition.
    				String localServerIp = localHostServer.hostServerConfig.managementIp;
    				HostServer remoteHostServer = currentNode.vmInstance.hostServer;
    				String remoteServerIp = remoteHostServer.hostServerConfig.managementIp;
    				
    				int localPort = localHostServer.tunnelPortMap.get(remoteServerIp).intValue();
    				int remotePort = remoteHostServer.tunnelPortMap.get(localServerIp).intValue();
    				
    				//first, push flow rules on hitSwitch. Without changing the mac address, push 
    				//The flow to the localPort on hitSwitch.
    				Match flowMatch = createMatch(hitSwitch, inPort, srcIp, dstIp,
							  					  transportProtocol, srcPort, dstPort);
    				OFFlowMod flowMod = createFlowMod(hitSwitch, flowMatch, 
	                          					      MacAddress.of(currentNode.getMacAddress(0)),
	                          					  	  OFPort.of(localPort));
    				hitSwitch.write(flowMod);
    				
    				flowMatch = createMatch(nodeSwitch, OFPort.of(remotePort), srcIp, dstIp,
		  					                transportProtocol, srcPort, dstPort);
    				flowMod = createFlowMod(nodeSwitch, flowMatch, 
    					                    MacAddress.of(currentNode.getMacAddress(0)),
    					                    OFPort.of(currentNode.getPort(0)));
    				nodeSwitch.write(flowMod);
    				
    				
    				RouteTuple routeTuple = new RouteTuple(srcIp.getInt(), dstIp.getInt(), 
    		       transportProtocol.equals(IpProtocol.TCP)?RouteTuple.TCP:RouteTuple.UDP,
    		    								     srcPort.getPort(), dstPort.getPort(), 
    		    				   DatapathId.of(currentNode.getBridgeDpid(0)).getLong());
    		    				
    		    	this.routeMap.put(routeTuple, currentNode.getManagementIp());
    			}
    			
    			currentNode.addActiveFlow();
    			hitSwitch = this.switchService.getSwitch(DatapathId.of(currentNode.getBridgeDpid(1)));
    			inPort = OFPort.of(currentNode.getPort(1));
    			localHostServer = currentNode.vmInstance.hostServer;
    		}
    	}
    }
    
    private Match createMatch(IOFSwitch sw, OFPort inPort, 
    						  IPv4Address srcIp, IPv4Address dstIp, IpProtocol transportProtocol,
    						  TransportPort srcPort, TransportPort dstPort){
    	Match.Builder mb = sw.getOFFactory().buildMatch();
    	mb.setExact(MatchField.IN_PORT, inPort);
    	
        mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
        .setExact(MatchField.IPV4_SRC, srcIp)
        .setExact(MatchField.IPV4_DST, dstIp);
        
        if(transportProtocol.equals(IpProtocol.TCP)){
			mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
			.setExact(MatchField.TCP_SRC, srcPort)
			.setExact(MatchField.TCP_DST, dstPort);
        }
        else{
    		mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
    		.setExact(MatchField.UDP_SRC, srcPort)
    		.setExact(MatchField.UDP_DST, dstPort);
        }
       
        return mb.build();
    }
    
    private OFFlowMod createFlowMod(IOFSwitch sw, Match flowMatch, MacAddress dstMac, OFPort outPort){
		List<OFAction> actionList = new ArrayList<OFAction>();	
		OFActions actions = sw.getOFFactory().actions();
		OFOxms oxms = sw.getOFFactory().oxms();
		
		actionList.add(actions.setField(oxms.ethDst(dstMac)));
		actionList.add(actions.output(outPort, Integer.MAX_VALUE));
		
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
		fmb.setHardTimeout(0);
		fmb.setIdleTimeout(15);
		fmb.setBufferId(OFBufferId.NO_BUFFER);
		fmb.setCookie(U64.of(8617));
		fmb.setPriority(5);
		fmb.setOutPort(outPort);
		fmb.setActions(actionList);
		fmb.setMatch(flowMatch);
		
		Set<OFFlowModFlags> sfmf = new HashSet<OFFlowModFlags>();
		sfmf.add(OFFlowModFlags.SEND_FLOW_REM);
		fmb.setFlags(sfmf);
		
		return fmb.build();
    }
    
    private net.floodlightcontroller.core.IListener.Command processPktRemoved(IOFSwitch sw, OFFlowRemoved msg){
    	if (!msg.getCookie().equals(U64.of(8617))) {
			return Command.CONTINUE;
		}
    	
    	Match match = msg.getMatch();
    	IPv4Address srcIp = match.get(MatchField.IPV4_SRC);
    	IPv4Address dstIp = match.get(MatchField.IPV4_DST);
    	IpProtocol protocol = match.get(MatchField.IP_PROTO);
    	TransportPort srcPort = null;
    	TransportPort dstPort = null;
    	if(protocol.equals(IpProtocol.TCP)){
    		srcPort = match.get(MatchField.TCP_SRC);
    		dstPort = match.get(MatchField.TCP_DST);
    	}
    	else{
    		srcPort = match.get(MatchField.UDP_SRC);
    		dstPort = match.get(MatchField.UDP_DST);
    	}
    	
    	RouteTuple tuple = new RouteTuple(srcIp.getInt(), dstIp.getInt(),
    					protocol.equals(IpProtocol.TCP)?RouteTuple.TCP:RouteTuple.UDP,
    							srcPort.getPort(), dstPort.getPort(), sw.getId().getLong());
    	
    	if(this.routeMap.containsKey(tuple)){
    		String managementIp = this.routeMap.get(tuple);
    		this.dpServiceChain.getNode(managementIp).deleteActiveFlow();
    		
    		//System.out.println("Flow on node "+managementIp+" is removed");
    	}
    	return Command.STOP;
    }
    @Override
    public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
    	switch (msg.getType()) {
    	case PACKET_IN:
    		return this.processPktIn(sw,msg,cntx);
    	case FLOW_REMOVED:
    		return this.processPktRemoved(sw, (OFFlowRemoved)msg);
		default:
			return Command.CONTINUE;
    	}
    }
}
