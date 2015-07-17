package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
 
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.MacAddress;
 
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import net.floodlightcontroller.core.IFloodlightProviderService;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Set;
import net.floodlightcontroller.packet.Ethernet;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.userauth.UserAuthException;
import java.util.concurrent.TimeUnit;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.xfer.FileSystemFile;
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
public class NFVTest implements IOFMessageListener, IFloodlightModule {
 
	protected IFloodlightProviderService floodlightProvider;
	protected Set<Long> macAddresses;
	protected static Logger logger;
	
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
        return l;
    }
 
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        macAddresses = new ConcurrentSkipListSet<Long>();
        logger = LoggerFactory.getLogger(NFVTest.class);
    }
 
    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        
        System.out.println("Start testing sshj");
        final SSHClient client = new SSHClient();
        try{
        	client.loadKnownHosts();
        	client.loadKnownHosts();
        	client.loadKnownHosts();
        	client.connect("net-b6.cs.hku.hk");
        	try{
        		client.authPassword("net", "netexplo");
        		
        		//final Session session = client.startSession();
        		//final Session.Command cmd = session.exec("sudo -s");
        		//cmd.join(1, TimeUnit.SECONDS);
        		//System.out.println(IOUtils.readFully(cmd.getInputStream()).toString());
        		//System.out.println("\n** exit status: " + cmd.getExitStatus());
        		
        		final Session session1 = client.startSession();
        		final Session.Command cmd1 = session1.exec("virsh create /home/net/domain-xml/img2.xml");	
        		cmd1.join(1, TimeUnit.SECONDS);
        		System.out.println(IOUtils.readFully(cmd1.getInputStream()).toString());
        		System.out.println("\n** exit status: " + cmd1.getExitStatus());
        		
        			 //final String fileSrc = "/home/jpduan/Desktop/ubuntu-img/ubuntu-14.04.2-raw.img";
        			 //final String remoteSrc = "/home/net/";
        			 //client.newSCPFileTransfer().upload(new FileSystemFile(fileSrc), remoteSrc);
        	}
        	catch (UserAuthException e){
        		System.out.println("failed to authenticate");
        	}
        }
        catch (IOException e){
        	System.out.println("IO error" + e);
        }
        
        
    }
 
    @Override
    public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
         Ethernet eth =
                 IFloodlightProviderService.bcStore.get(cntx,
                                             IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
  
         Long sourceMACHash = eth.getSourceMACAddress().getLong();
         if (!macAddresses.contains(sourceMACHash)) {
             macAddresses.add(sourceMACHash);
             logger.info("MAC Address: {} seen on switch: {}",
                     eth.getSourceMACAddress().toString(),
                     sw.getId().toString());
         }
         return Command.CONTINUE;
     }
 
}