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
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpDscp;
import org.projectfloodlight.openflow.types.IpEcn;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
 
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import net.floodlightcontroller.core.IFloodlightProviderService;
import java.util.ArrayList;
import java.util.Set;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;

import net.floodlightcontroller.nfvtest.message.MessageHub;
import net.floodlightcontroller.nfvtest.localcontroller.LocalController;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.AddHostServerRequest;
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
import net.floodlightcontroller.nfvtest.nfvutils.RouteTuple;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
 
public class NFVTest implements IOFMessageListener, IFloodlightModule {
 
	protected IFloodlightProviderService floodlightProvider;
	protected static Logger logger;
    protected IOFSwitchService switchService;

	private NFVServiceChain dpServiceChain;
	private NFVServiceChain cpServiceChain;
	
	private LocalController localController;
	private VmAllocator vmAllocator;
	private HashMap<FlowTuple, Integer> flowMap;
	private HashMap<RouteTuple, String> routeMap;
	private Context zmqContext;
	private MessageHub mh;
	private VmWorker vmWorker;
	private SubscriberConnector subscriberConnector;
	private DNSUpdator dnsUpdator;
	private ServiceChainHandler chainHandler;
	
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
        logger = LoggerFactory.getLogger(NFVTest.class);
        zmqContext = ZMQ.context(1);
        
        logger.info("start testing network xml");

        //create controller config and host server configi
        int bladeIndex = 1;
        String userName = "*";
		String password = "*";
		String gIp = "202.45.128.147";
		String mIp = "202.45.128."+new Integer(145+bladeIndex).toString();
		String pIp = "202.45.128."+new Integer(145+bladeIndex).toString();
		String iIp = "202.45.128."+new Integer(145+bladeIndex).toString();
		String entryIp = "10."+new Integer(bladeIndex).toString()+".1.1";
		String gatewayIp = "10."+new Integer(bladeIndex).toString()+".5.1";
		String exitIp  = "10."+new Integer(bladeIndex).toString()+".9.1";
		int middle = 160+bladeIndex;
		ControllerConfig controllerConfig = 
				new ControllerConfig(mIp, "/home/net/base-env", "basexml.xml", "networkxml.xml");
		HostServerConfig hostServerConfig = 
				new HostServerConfig(mIp, iIp, pIp, 48, 80*1024, 100*1024, 1, userName, password, "/home/net/nfvenv");
		byte[] prefix = new byte[3];
		prefix[0] = 0x52;
		prefix[1] = 0x54;
		prefix[2] = 0x00;
		MacAddressAllocator macAllocator = new MacAddressAllocator(prefix);
		IpAddressAllocator ipAllocator = new IpAddressAllocator(192,middle,64);
		
		this.flowMap = new HashMap<FlowTuple, Integer>();
		this.routeMap = new HashMap<RouteTuple, String>();
		
		
		//create service chain configuration for control plane
		StageVmInfo bonoInfo = new StageVmInfo(1,2*1024,2*1024,"bono.img", 80, 2, -1);
		StageVmInfo sproutInfo = new StageVmInfo(1,2*1024,2*1024,"sprout.img", 80, 2, -1);
		ArrayList<StageVmInfo> cpList = new ArrayList<StageVmInfo>();
		cpList.add(bonoInfo);
		cpList.add(sproutInfo);
		ServiceChainConfig cpServiceChainConfig = new ServiceChainConfig("CONTROL", 2, cpList);
		
		//create data plane service chain configuration
		StageVmInfo firewallInfo = new StageVmInfo(2, 2*1024, 2*1024, "firewall_mini_v2.img", 45, -1, 50000);
		StageVmInfo ipsInfo = new StageVmInfo(2, 2*1024, 2*1024, "snort_mini_v2.img", 45, -1, 20000);
		StageVmInfo transcoderInfo = new StageVmInfo(2, 2*1024, 2*1024, "transcoder_mini_v2.img", 45, -1, 13000);
		ArrayList<StageVmInfo> dpList = new ArrayList<StageVmInfo>();
		dpList.add(firewallInfo);
		dpList.add(ipsInfo);
		dpList.add(transcoderInfo);
		ServiceChainConfig dpServiceChainConfig = new ServiceChainConfig("DATA", 3, dpList);
		
		//create HostServer 
		HashMap<String, ServiceChainConfig> map = new HashMap<String, ServiceChainConfig>();
		map.put(cpServiceChainConfig.name, cpServiceChainConfig);
		map.put(dpServiceChainConfig.name, dpServiceChainConfig);
		HostServer hostServer = new HostServer(controllerConfig, hostServerConfig, map, macAllocator,
										ipAllocator, entryIp, gatewayIp, exitIp);
		
		this.cpServiceChain = new NFVServiceChain(cpServiceChainConfig);
		this.dpServiceChain = new NFVServiceChain(dpServiceChainConfig);
		
		mh = new MessageHub();
		
		vmWorker = new VmWorker("vmWorker");
		vmWorker.registerWithMessageHub(mh);
		
		vmAllocator = new VmAllocator("vmAllocator", 100);
		vmAllocator.registerWithMessageHub(mh);
		
		
		subscriberConnector = new SubscriberConnector("subscriberConnector",zmqContext);
		subscriberConnector.registerWithMessageHub(mh);
		
		dnsUpdator = new DNSUpdator("dnsUpdator", "192.168.126.123", "7773", zmqContext);
		dnsUpdator.registerWithMessageHub(mh);
		dnsUpdator.connect();
		
		chainHandler = new ServiceChainHandler("chainHandler", zmqContext, this.switchService);
		chainHandler.registerWithMessageHub(mh);
		chainHandler.startPollerThread();
		chainHandler.addServiceChain(cpServiceChain);
		chainHandler.addServiceChain(dpServiceChain);
		chainHandler.addHostServer(hostServer);
		
		mh.startProcessors();
		
		HostInitializationRequest m = new HostInitializationRequest("hehe",hostServer);
		mh.sendTo("vmWorker", m);
		try{
			Thread.sleep(5000);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		AddHostServerRequest m2 = new AddHostServerRequest("hehe", hostServer);
		mh.sendTo("vmAllocator", m2);
		try{
			Thread.sleep(200);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		int c[] = {100, 30, 80};
		localController = new LocalController(gIp, 5555, 5556, 5557, 5558, 5559, mIp, 
				true, 2000, c, mh, zmqContext, hostServer.entryIp, chainHandler);
		Thread lcThread = new Thread(localController);
		lcThread.start();
       
    }
 
    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        //floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
        //floodlightProvider.addOFMessageListener(OFType.ERROR, this);
    }
    
    private net.floodlightcontroller.core.IListener.Command processPktIn(IOFSwitch sw, OFMessage msg, FloodlightContext cntx){
    	Ethernet eth =
                IFloodlightProviderService.bcStore.get(cntx,
                                            IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        IPacket pkt = eth.getPayload(); 
        OFPacketIn pi = (OFPacketIn)msg;
        if (eth.isBroadcast() || eth.isMulticast()) {
            if (pkt instanceof ARP) {   
    			handleArp(eth, sw, pi);
    			return Command.STOP;
            }
        } 
        
        if(pkt instanceof IPv4){
        	int switchStageIndex = vmAllocator.dpidStageIndexMap.get(sw.getId());
       	 	if( switchStageIndex == 0 ){
       	 		int inputPort = pi.getMatch().get(MatchField.IN_PORT).getPortNumber();
       	 		//the input port is either the gateway port, or the inter-dc tunnel port, we
       	 		//don't process input traffic comming from other port
       	 		HostServer inputHostServer = vmAllocator.dpidHostServerMap.get(sw.getId());
       	 		if((inputPort==inputHostServer.gatewayPort)||(inputHostServer.portDcIndexMap.containsKey(inputPort))){
       	 		
	       	 		serviceChainLoadBalancing(sw, cntx, pi.getMatch().get(MatchField.IN_PORT),
	       	 				vmAllocator.dpidHostServerMap.get(sw.getId()));
	       	 		return Command.STOP;
       	 		}
       	 		else{
       	 			return Command.STOP;
       	 		}
       	 	}
        }
        
        return Command.STOP;
    }
    
    private void handleArp(Ethernet eth, IOFSwitch sw, OFPacketIn pi){
    	
    	//Let's check which stage is this switch
    	HashMap<DatapathId, Integer> dpidStageIndexMap = vmAllocator.dpidStageIndexMap;
    	HostServer hostServer = vmAllocator.dpidHostServerMap.get(sw.getId());
    	int switchStageIndex = dpidStageIndexMap.get(sw.getId()).intValue();
    	String switchDpid = hostServer.serviceChainDpidMap.get("DATA").get(switchStageIndex);
    	OFPort inPort = pi.getMatch().get(MatchField.IN_PORT);
    	
    	ARP arpRequest = (ARP) eth.getPayload();
    	
    	if( (switchStageIndex == 0)&&(inPort.getPortNumber() == hostServer.gatewayPort) ){
    		//We got ARP request from the gatewayPort, check whether we need to answer this arp request.
    		IPv4Address targetAddr = arpRequest.getTargetProtocolAddress();
    		if(targetAddr.equals(IPv4Address.of(hostServer.entryIp))){
    			//The ARP request comes from the traffic generator, handle it.
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
                //sw.flush();
    		}
    	}
    	else{
    		boolean flag = false;
			synchronized(this.dpServiceChain){
				//we check whether the arp comes from the exit end of a middlebox
				flag = this.dpServiceChain.exitFromNode(DatapathId.of(switchDpid), inPort.getPortNumber());
			}
			if(flag == true){
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
	            //sw.flush();
			}
    	}
    }
    
    private void serviceChainLoadBalancing(IOFSwitch sw, FloodlightContext cntx, OFPort initialInPort, 
    		HostServer inputHostServer){
    	Ethernet eth =
                IFloodlightProviderService.bcStore.get(cntx,
                                            IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        IPv4 ip_pkt = (IPv4)eth.getPayload();
        if(!ip_pkt.getProtocol().equals(IpProtocol.UDP)){
        	// we only process udp packet
        	return;
        }
        
        IPv4Address srcIp = ip_pkt.getSourceAddress();
        byte dscpEcn = ip_pkt.getDiffServ();
        
    	UDP udp_pkt = (UDP)ip_pkt.getPayload();
    	IpProtocol transportProtocol = IpProtocol.UDP;
    	TransportPort srcPort = udp_pkt.getSourcePort();
    	
    	//System.out.println("sdn controller receives flow srcIp:"+srcIp.toString()+" srcPort:"+srcPort.toString()
    	//+" dstIp:"+dstIp.toString()+" dstPort:"+dstPort.toString());

        OFPort inPort = initialInPort;
        	
        int srcDstPair[] = null; //array containing srcDcIndex and dstDcIndex
    	int scalingInterval = 0; //the current scaling interval
    	int dpPaths[] = null;    //dpPaths of the current scaling interval
    	ArrayList<Integer> dpDcPath = null;
    	int currentDcIndex = localController.getCurrentDcIndex();
    	
    	synchronized(this.dpServiceChain){
	    	if(inPort.getPortNumber() == inputHostServer.gatewayPort){
	    		//This is the new flow, we need to query local controller to know where exactly this flow want
	    		//to go.
	    		String srcAddr = srcIp.toString()+":"+srcPort.toString();
	    		srcDstPair = localController.getSrcDstPair(srcAddr);
	    		scalingInterval = this.dpServiceChain.getScalingInterval();
	    		dpPaths = this.dpServiceChain.getCurrentDpPaths(srcDstPair[0], srcDstPair[1]);
	    		dpDcPath = this.dpServiceChain.getCurrentDpDcPath(srcDstPair[0], srcDstPair[1]);
	    	}
	    	else{
	    		//This is not a new flow, we need to check the tagging
	    		//The destination port contains scaling interval
	    		//the destination Ip address contains dp path src dst pair.
	    		//0x0000FF00>>8 is the dst dc index
	    		//0x000000FF is the src dc index
	    		int srcIndex = (int)((dscpEcn&0xE0)>>5);
	    		int dstIndex = (int)((dscpEcn&0x1C)>>2);
	    		srcDstPair = new int[2];
	    		srcDstPair[0] = srcIndex;
	    		srcDstPair[1] = dstIndex;
	    		
	    		scalingInterval = (int)(dscpEcn&0x03);
	    		int currentScalingInterval = this.dpServiceChain.getScalingInterval();
	    		
	    		
	    		//System.out.println("got a flow from another datacenter, src dc: "+new Integer(srcDstPair[0]).toString()+
	    		//		" dst dc: "+new Integer(srcDstPair[1]).toString() + " scalingInterval: "+new Integer(scalingInterval).toString());
	    		//System.out.println("The current scaling interval is: " + new Integer(currentScalingInterval).toString());
	    		//the scaling interval loops through 0-3. 
	    		if(scalingInterval == currentScalingInterval){
	    			//System.out.println("use current path");
	    			dpPaths = this.dpServiceChain.getCurrentDpPaths(srcDstPair[0], srcDstPair[1]);
	    			dpDcPath = this.dpServiceChain.getCurrentDpDcPath(srcDstPair[0], srcDstPair[1]);
	    			String pathOutput = "";
	    			for(int i=0; i<dpPaths.length; i++){
	    				pathOutput = pathOutput + new Integer(dpPaths[i]).toString() + " ";
	    			}
	    			//System.out.println("The path is: "+pathOutput);
	    		}
	    		else if(((scalingInterval+1)%4)==currentScalingInterval){
	    			//System.out.println("use previous path");
	    			dpPaths = this.dpServiceChain.getPreviousDpPaths(srcDstPair[0], srcDstPair[1]);
	    			dpDcPath = this.dpServiceChain.getPreviousDpDcPath(srcDstPair[0], srcDstPair[1]);
	    			String pathOutput = "";
	    			for(int i=0; i<dpPaths.length; i++){
	    				pathOutput = pathOutput + new Integer(dpPaths[i]).toString() + " ";
	    			}
	    			//System.out.println("The path is: "+pathOutput);
	    		}
	    		else if(((scalingInterval+4-1)%4)==currentScalingInterval){
	    			//System.out.println("use next path");
	    			dpPaths = this.dpServiceChain.getNextDpPaths(srcDstPair[0], srcDstPair[1]);
	    			dpDcPath = this.dpServiceChain.getNextDpDcPath(srcDstPair[0], srcDstPair[1]);
	    			String pathOutput = "";
	    			for(int i=0; i<dpPaths.length; i++){
	    				pathOutput = pathOutput + new Integer(dpPaths[i]).toString() + " ";
	    			}
	    			//System.out.println("The path is: "+pathOutput);
	    		}
	    		else{
	    			logger.info("routing error");
	    			return;
	    		}
	    	}
    	}
    	
    	//Now let's verify whether the packet is a correct packet
    	if((srcDstPair[0]!=dpDcPath.get(0))||(srcDstPair[1]!=dpDcPath.get(dpDcPath.size()-1))){
    		//System.out.println("we are getting an incorrect packet with unmatching service chain path");
    		return;
    	}
		
    	boolean currentDcOnPath = false;
    	int pos = 0;
    	for(; pos<dpDcPath.size(); pos++){
    		if(dpDcPath.get(pos)==currentDcIndex){
    			currentDcOnPath = true;
    			break;
    		}
    	}
    	
    	if(currentDcOnPath == false){
    		return;
    	}
    	
    	ArrayList<Integer> stageList = new ArrayList<Integer>();
		for(int i=0; i<dpPaths.length; i++){
			if(dpPaths[i] == currentDcIndex){
				stageList.add(new Integer(i));
			}
		}
		List<NFVNode> routeList= null;
		if(stageList.size()>0){
			routeList= this.dpServiceChain.forwardRoute(stageList.get(0).intValue(), 
					stageList.get(stageList.size()-1).intValue());
		}
		else{
			routeList = new ArrayList<NFVNode>();
		}
		byte[] newDstAddr = new byte[4];
		newDstAddr[0] = 0;
		newDstAddr[1] = 0;
		newDstAddr[2] = 0;
		newDstAddr[3] = 0;
		for(int i=0; i<routeList.size(); i++){
			NFVNode currentNode = routeList.get(i);
			int nodeIndex = currentNode.getIndex();
			newDstAddr[stageList.get(i)] = (byte)nodeIndex;
		}
		if(pos!=dpDcPath.size()-1){
			int nextDcIndex = dpDcPath.get(pos+1);
			if(stageList.size()>0){
				int lastStage = stageList.get(stageList.size()-1);
				newDstAddr[lastStage+1] = (byte)nextDcIndex;
			}
			else{
				newDstAddr[0] = (byte)nextDcIndex;
			}
		}
    	
		IOFSwitch hitSwitch = sw;
		
    	if(pos == 0){
    		//routing on entry datacenter
    		Match flowMatch = createMatch(hitSwitch, inPort, srcIp, transportProtocol, srcPort);
			OFFlowMod flowMod = createEntryFlowMod(hitSwitch, flowMatch, MacAddress.of(routeList.get(0).getMacAddress(0)), 
					OFPort.of(inputHostServer.statInPort), srcDstPair[0], srcDstPair[1], scalingInterval, IPv4Address.of(newDstAddr));
			hitSwitch.write(flowMod);
			hitSwitch.flush();
    		
    	}
    	else if(pos == dpDcPath.size()-1){
    		//routing on exit datacenter
    		//we got a flow coming from another datacenter, let's route the flow to 
			//proper location
    		if(stageList.size()!=0){
				int incomingDcIndex = inputHostServer.portDcIndexMap.get(new Integer(initialInPort.getPortNumber()));
				int toThisPort = inputHostServer.dcIndexPatchPortListMap.get(new Integer(incomingDcIndex)).get(stageList.get(0)).intValue();
				
				//System.out.println("got a flow comming from datacenter: "+new Integer(incomingDcIndex).toString());
				//String printSth = "the patch port list is :";
				//ArrayList<Integer> array = inputHostServer.dcIndexPatchPortListMap.get(new Integer(incomingDcIndex));
				//for(int i=0; i<array.size(); i++){
					//printSth = printSth+array.get(i).toString()+" ";
				//}
				//System.out.println(printSth);
				//System.out.println("The flow comes from datacenter: "+new Integer(incomingDcIndex).toString());
				//System.out.println("The flow will be sent to this port: "+new Integer(toThisPort).toString());
				
				Match flowMatch = createMatch(sw, initialInPort, srcIp, transportProtocol, srcPort);
				OFFlowMod flowMod = createFlowModFromOtherDc(sw, flowMatch, IPv4Address.of(newDstAddr), OFPort.of(toThisPort));
				sw.write(flowMod);
				sw.flush();
    		}
			
			//This is the last stage
			//String print="the stage list is: ";
			//for(int i=0; i<stageList.size(); i++){
				//print  = print + stageList.get(i).toString()+" ";
			//}
			//System.out.println(print);
			//System.out.println(" the src is: "+new Integer(srcDstPair[0]).toString()+" the dst is: "+new Integer(srcDstPair[1]).toString());
			//print = "the path is: ";
			//for(int i=0; i<dpPaths.length; i++){
				//print = print + new Integer(dpPaths[i])+" ";
			//}
			//System.out.println(print);
					
			String exitFlowSrcAddr = srcIp.toString()+":"+srcPort.toString();
			//System.out.println(" exitFlowSrcAddr: "+exitFlowSrcAddr+" is going to access the map");
			String exitFlowDstAddr = localController.getExitFlowDstAddr(exitFlowSrcAddr);
			String exitFlowSrcIp   = localController.getExitFlowSrcIp(exitFlowSrcAddr);
			
			if(exitFlowDstAddr.equals("")||exitFlowSrcIp.equals("")){
				return;
			}
			
			String sArray[] = exitFlowDstAddr.split(":");
			String exitFlowDstip = sArray[0];
			String exitFlowDstPort = sArray[1];
			
			OFPort exitPort = (stageList.size()!=0)?OFPort.of(inputHostServer.patchPort):inPort;
			Match flowMatch = createMatch(sw, exitPort, srcIp,transportProtocol, srcPort);
			
			OFFlowMod flowMod = createExitFlowMod(sw, flowMatch, MacAddress.of(inputHostServer.gatewayMac), 
					OFPort.of(inputHostServer.gatewayPort), exitFlowSrcIp, exitFlowDstip, Integer.parseInt(exitFlowDstPort), "udp");
			sw.write(flowMod);
			sw.flush();
    	}
    	else{
    		//routing on intermediate datacenter
    		//we got a flow coming from another datacenter, let's route the flow to 
			//proper location
			int incomingDcIndex = inputHostServer.portDcIndexMap.get(new Integer(initialInPort.getPortNumber()));
			int toThisPort = inputHostServer.dcIndexPatchPortListMap.get(new Integer(incomingDcIndex)).get(stageList.get(0)).intValue();
			
			//System.out.println("got a flow comming from datacenter: "+new Integer(incomingDcIndex).toString());
			//String printSth = "the patch port list is :";
			//ArrayList<Integer> array = inputHostServer.dcIndexPatchPortListMap.get(new Integer(incomingDcIndex));
			//for(int i=0; i<array.size(); i++){
				//printSth = printSth+array.get(i).toString()+" ";
			//}
			//System.out.println(printSth);
			//System.out.println("The flow comes from datacenter: "+new Integer(incomingDcIndex).toString());
			//System.out.println("The flow will be sent to this port: "+new Integer(toThisPort).toString());
			
			
			Match flowMatch = createMatch(sw, initialInPort, srcIp, transportProtocol, srcPort);
			OFFlowMod flowMod = createFlowModFromOtherDc(sw, flowMatch, IPv4Address.of(newDstAddr), OFPort.of(toThisPort));
			sw.write(flowMod);
			sw.flush();

			//inPort = OFPort.of(inputHostServer.dcIndexPortMap.get(incomingDcIndex));
    	}
    }
    
    private Match createMatch(IOFSwitch sw, OFPort inPort, 
    						  IPv4Address srcIp, IpProtocol transportProtocol,
    						  TransportPort srcPort){
    	Match.Builder mb = sw.getOFFactory().buildMatch();
    	mb.setExact(MatchField.IN_PORT, inPort);
    	
        mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
        .setExact(MatchField.IPV4_SRC, srcIp);
        
        if(transportProtocol.equals(IpProtocol.TCP)){
			mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
			.setExact(MatchField.TCP_SRC, srcPort);
        }
        else{
    		mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
    		.setExact(MatchField.UDP_SRC, srcPort);
        }
       
        return mb.build();
    }
    
    private OFFlowMod createFlowModFromOtherDc(IOFSwitch sw, Match flowMatch, IPv4Address dstAddr, OFPort outPort){
		List<OFAction> actionList = new ArrayList<OFAction>();	
		OFActions actions = sw.getOFFactory().actions();
		OFOxms oxms = sw.getOFFactory().oxms();
		actionList.add(actions.setField(oxms.ipv4Dst(dstAddr)));
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
    
    private OFFlowMod createEntryFlowMod(IOFSwitch sw, Match flowMatch, MacAddress dstMac, OFPort outPort, 
    		int srcDcIndex, int dstDcIndex, int scalingInterval, IPv4Address dstAddr){
    	byte ecn = (byte)(scalingInterval%4);
    	byte dscp = (byte)(((srcDcIndex&0x07)<<3)+((dstDcIndex&0x07)));
    	
    	List<OFAction> actionList = new ArrayList<OFAction>();	
		OFActions actions = sw.getOFFactory().actions();
		OFOxms oxms = sw.getOFFactory().oxms();
		actionList.add(actions.setField(oxms.ethDst(dstMac)));
		actionList.add(actions.setField(oxms.ipEcn(IpEcn.of(ecn))));
		actionList.add(actions.setField(oxms.ipDscp(IpDscp.of(dscp))));
		actionList.add(actions.setField(oxms.ipv4Dst(dstAddr)));
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
    
    private OFFlowMod createExitFlowMod(IOFSwitch sw, Match flowMatch, MacAddress dstMac, OFPort outPort, 
    		String srcIp, String dstIp, int dstPort, String udpOrTcp){
		List<OFAction> actionList = new ArrayList<OFAction>();	
		OFActions actions = sw.getOFFactory().actions();
		OFOxms oxms = sw.getOFFactory().oxms();
		
		actionList.add(actions.setField(oxms.ethDst(dstMac)));
		actionList.add(actions.setField(oxms.ipv4Src(IPv4Address.of(srcIp))));
		actionList.add(actions.setField(oxms.ipv4Dst(IPv4Address.of(dstIp))));
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
   
    @Override
    public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
    	switch (msg.getType()) {
    	case PACKET_IN:
    		return this.processPktIn(sw,msg,cntx);
		default:
			return Command.CONTINUE;
    	}
    }
}