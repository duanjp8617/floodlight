package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;
 
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
 
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
        
        this.IpsServer1 = new IpsServer();
        this.IpsServer1.attachIngressIf("192.168.56.11", 
        		"52:54:00:69:0e:af", "00:00:3e:36:fa:a6:3d:4c", 26);
        this.IpsServer1.attachEgressIf("192.168.57.11",
        		"52:54:00:a7:a0:af", "00:00:2a:92:11:2c:36:49", 10);
        
        this.IpsServer2 = new IpsServer();
        this.IpsServer2.attachIngressIf("192.168.56.12", 
        		"52:54:00:a6:ec:a7", "00:00:3e:36:fa:a6:3d:4c", 25);
        this.IpsServer2.attachEgressIf("192.168.57.12",
        		"52:54:00:7b:45:6b", "00:00:2a:92:11:2c:36:49", 9);
        
        this.IpsServer3 = new IpsServer();
        this.IpsServer3.attachIngressIf("192.168.56.13", 
        		"52:54:00:6a:7e:06", "00:00:3e:36:fa:a6:3d:4c", 27);
        this.IpsServer3.attachEgressIf("192.168.57.13",
        		"52:54:00:36:96:70", "00:00:2a:92:11:2c:36:49", 11);
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
         
         if(pkt instanceof IPv4){
        	 IPv4 ip_pkt = (IPv4)pkt;
        	 logger.info("source IP: {}, destination IP: {}",
        			     ip_pkt.getSourceAddress().toString(), 
        			     ip_pkt.getDestinationAddress().toString());
         }
         /*Long sourceMACHash = eth.getSourceMACAddress().getLong();
         if (!macAddresses.contains(sourceMACHash)) {
             macAddresses.add(sourceMACHash);
             logger.info("MAC Address: {} seen on switch: {}",
                     eth.getSourceMACAddress().toString(),
                     sw.getId().toString());
             Collection<OFPort> switchPorts = sw.getEnabledPortNumbers();
             
             for(OFPort port : switchPorts){
            	 logger.info("switch: {} has port number: {}", sw.getId().toString(),
            			 port.getPortNumber());
             }
         }*/
         return Command.CONTINUE;
     }
 
}