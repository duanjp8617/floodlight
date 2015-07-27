package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
 
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
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


 
public class NFVTest implements IOFMessageListener, IFloodlightModule {
	
	public class IpsServer {
		public class Interface {
			public int ipAddress;
			public MacAddress macAddress;
			public DatapathId attachedSwitch;
			public int port;
			
			public Interface(String ipAddress, String macAddress, String dpid, int port){
				this.ipAddress = IPv4.toIPv4Address(ipAddress);
				this.macAddress = MacAddress.of(macAddress);
				this.attachedSwitch = DatapathId.of(dpid);
				this.port = port;
			}
		}
		
		public Interface ingressIf;
		public Interface egressIf;
		
		public IpsServer(){
		}
		
		public void attachIngressIf(String ipAddress, String macAddress, String dpid, int port){
			this.ingressIf = new Interface(ipAddress, macAddress, dpid, port);
		}
		
		public void attachEgressIf(String ipAddress, String macAddress, String dpid, int port){
			this.egressIf = new Interface(ipAddress, macAddress, dpid, port);
		}
	}
 
	protected IFloodlightProviderService floodlightProvider;
	protected Set<Long> macAddresses;
	protected static Logger logger;
    protected IOFSwitchService switchService;
    
    protected IpsServer IpsServer1;
    protected IpsServer IpsServer2;
    protected IpsServer IpsServer3;
    
    protected ArrayList<IpsServer> ipsServerList;
    
    private String dpid_br1 = "00:00:3e:36:fa:a6:3d:4c";
    private String dpid_br2 = "00:00:2a:92:11:2c:36:49";
	
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
        return false;
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
        ipsServerList = new ArrayList<IpsServer>();
        
        this.IpsServer1 = new IpsServer();
        this.IpsServer1.attachIngressIf("192.168.56.11", 
        		"52:54:00:69:0e:af", "00:00:3e:36:fa:a6:3d:4c", 26);
        this.IpsServer1.attachEgressIf("192.168.57.11",
        		"52:54:00:a7:a0:af", "00:00:2a:92:11:2c:36:49", 10);
        ipsServerList.add(this.IpsServer1);
        
        this.IpsServer2 = new IpsServer();
        this.IpsServer2.attachIngressIf("192.168.56.12", 
        		"52:54:00:a6:ec:a7", "00:00:3e:36:fa:a6:3d:4c", 25);
        this.IpsServer2.attachEgressIf("192.168.57.12",
        		"52:54:00:7b:45:6b", "00:00:2a:92:11:2c:36:49", 9);
        ipsServerList.add(this.IpsServer2);
        
        this.IpsServer3 = new IpsServer();
        this.IpsServer3.attachIngressIf("192.168.56.13", 
        		"52:54:00:6a:7e:06", "00:00:3e:36:fa:a6:3d:4c", 27);
        this.IpsServer3.attachEgressIf("192.168.57.13",
        		"52:54:00:36:96:70", "00:00:2a:92:11:2c:36:49", 11);
        ipsServerList.add(this.IpsServer3);
    }
 
    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }
 
    @Override
    public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
         Ethernet eth =
                 IFloodlightProviderService.bcStore.get(cntx,
                                             IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
         IPacket pkt = eth.getPayload(); 

         OFPacketIn pi = (OFPacketIn)msg;
         if(pkt instanceof IPv4){
        	 IPv4 ip_pkt = (IPv4)pkt;
        	 
        	 int destIpAddress = ip_pkt.getDestinationAddress().getInt();
        	 
        	 if(destIpAddress == IPv4Address.of("192.168.57.51").getInt()){
        		 if(sw.getId().getLong() == DatapathId.of(this.dpid_br1).getLong()){
        			 //Only care about flows sent to the entry switch.
        			 logger.info("receive flow, start testing simpleLoadBalancing");
        			 //simpleLoadBalancing(sw, cntx, pi.getMatch().get(MatchField.IN_PORT));
        			 //return Command.STOP;
        		 }
        	 }
         }
         
         return Command.CONTINUE;
    }
    
    private void simpleLoadBalancing(IOFSwitch sw, FloodlightContext cntx, OFPort inPort){
    	//First build a match from the received packet.
    	int sourcePort = 0;
    	
        Ethernet eth =
                IFloodlightProviderService.bcStore.get(cntx,
                                            IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        
        Match.Builder mb = sw.getOFFactory().buildMatch();
        
        //set the match for in port;
        mb.setExact(MatchField.IN_PORT, inPort);
        
        //set match for mac address.
        mb.setExact(MatchField.ETH_SRC, eth.getSourceMACAddress())
          .setExact(MatchField.ETH_DST, eth.getDestinationMACAddress());
        
        //set match for ip address.
        IPv4 ip_pkt = (IPv4)eth.getPayload();
        mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
          .setExact(MatchField.IPV4_SRC, ip_pkt.getSourceAddress())
          .setExact(MatchField.IPV4_DST, ip_pkt.getDestinationAddress());
        
        //set match for tcp/udp
        if(ip_pkt.getProtocol().equals(IpProtocol.TCP)){
        	TCP tcp_pkt = (TCP)ip_pkt.getPayload();
			mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
			.setExact(MatchField.TCP_SRC, tcp_pkt.getSourcePort())
			.setExact(MatchField.TCP_DST, tcp_pkt.getDestinationPort());
			
			sourcePort = tcp_pkt.getSourcePort().getPort();
        }
        else if(ip_pkt.getProtocol().equals(IpProtocol.UDP)){
          	UDP udp_pkt = (UDP)ip_pkt.getPayload();
    		mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
    		.setExact(MatchField.UDP_SRC, udp_pkt.getSourcePort())
    		.setExact(MatchField.UDP_DST, udp_pkt.getDestinationPort());
    		
    		sourcePort = udp_pkt.getSourcePort().getPort();
        }
        
        Match flowMatch = mb.build();
      
		List<OFAction> actionList = new ArrayList<OFAction>();	
		OFActions actions = sw.getOFFactory().actions();
		OFOxms oxms = sw.getOFFactory().oxms();
		
		//rewrite use of1.3
		int index = sourcePort%3;
		IpsServer selectedServer = ipsServerList.get(index);
		actionList.add(actions.setField(oxms.ethDst(selectedServer.ingressIf.macAddress)));
		actionList.add(actions.output(OFPort.of(selectedServer.ingressIf.port), Integer.MAX_VALUE));
		
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
		fmb.setHardTimeout(0);
		fmb.setIdleTimeout(10);
		fmb.setBufferId(OFBufferId.NO_BUFFER);
		fmb.setCookie(U64.of(0));
		fmb.setPriority(1);
		fmb.setOutPort(OFPort.of(selectedServer.ingressIf.port));
		fmb.setActions(actionList);
		fmb.setMatch(flowMatch);
		sw.write(fmb.build());
    }
   
 
}