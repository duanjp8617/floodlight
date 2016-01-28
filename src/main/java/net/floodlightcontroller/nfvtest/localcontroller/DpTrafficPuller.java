package net.floodlightcontroller.nfvtest.localcontroller;

import java.io.*;
import java.util.HashMap;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class DpTrafficPuller implements Runnable{
	private int pullInterval;
	private volatile boolean quit;
	private Context context;
	private Socket socket;
	private HashMap<Long, Long> map;
    private HashMap<Long, Long> previousMap;
	
	
	public DpTrafficPuller(int pullInterval, Context context){
		this.pullInterval = pullInterval;
		this.quit = false;
		this.socket = context.socket(ZMQ.PUSH);
		this.map = new HashMap<Long, Long>();
		this.previousMap = new HashMap<Long, Long>();
	}
	
	public void quit(){
		this.quit = true;
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		this.socket.connect("inproc://statPull");
		while(quit==false){
			try {
				Thread.sleep(pullInterval);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			ProcessBuilder builder = new ProcessBuilder(
	            "/bin/bash", "-c", "sudo ovs-ofctl dump-flows stat-br");
	        builder.redirectErrorStream(true);
	        Process p = null;;
			try {
				p = builder.start();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
	        
	        
	        this.socket.send("DATA", ZMQ.SNDMORE);
	        this.socket.send(new Integer(this.pullInterval).toString(), ZMQ.SNDMORE);
	        String line = null;
	        
	        while (true) {
	            try {
					line = r.readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
	            
	            if (line == null){ 
	            	break; 
	            }
	            else{
	            	if(line.indexOf("ip,nw_tos=")!=-1){
	            		 int startPos = line.indexOf("ip,nw_tos=");
	            		 startPos += 10;
	            		 int endPos = line.indexOf("actions=");
	            		 endPos -= 1;
	            		 String srcDstTag = line.substring(startPos, endPos);
	     
	            		 int srcDstVal = Integer.parseInt(srcDstTag);
	            		 long dstDcIndex = (long)((srcDstVal>>2)&(0x00000007));
	            		 
	            		 int byteStart = line.indexOf("n_packets=");
	            		 byteStart += 10;
	            		 int byteEnd   = line.indexOf(", n_bytes");
	            		 String n_bytes = line.substring(byteStart, byteEnd);
	            		 
	            		 map.put(dstDcIndex, Long.parseLong(n_bytes));//This is actually n_packets
	            	}
	            }
	        }
	        String statMat = "";
	        for(long i=0; i<map.size(); i++){
	        	long speed = map.get(i) - ((previousMap.containsKey(i))?previousMap.get(i):0);
	        	statMat = statMat + new Long(speed).toString() + " ";
	        	previousMap.put(i, map.get(i));
	        }

	        this.socket.send(statMat,0);
	        map.clear();
		}
	}
}
