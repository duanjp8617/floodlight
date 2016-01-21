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
	
	public DpTrafficPuller(int pullInterval, Context context){
		this.pullInterval = pullInterval;
		this.quit = false;
		this.socket = context.socket(ZMQ.PUSH);
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
	        String line = null;
	        
	        //this.socket.send("DATA", ZMQ.SNDMORE);
	        //this.socket.send(new Integer(this.pullInterval).toString(), ZMQ.SNDMORE);
	        HashMap<Integer, String> map = new HashMap<Integer, String>();
	        while (true) {
	            try {
					line = r.readLine();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	            if (line == null){ 
	            	break; 
	            }
	            else{
	            	if(line.indexOf("ip,nw_dst=")!=-1){
	            		 System.out.println(line);
	            		 
	            		 int startPos = line.indexOf("ip,nw_dst=");
	            		 startPos += 10;
	            		 int endPos = line.indexOf("actions=");
	            		 endPos -= 1;
	            		 String srcDstTag = line.substring(startPos, endPos);
	     
	            		 int firstDotPos = srcDstTag.indexOf(".");
	            		 String dstDcIndex = srcDstTag.substring(0, firstDotPos);
	            		 
	            		 int byteStart = line.indexOf("n_bytes=");
	            		 byteStart += 8;
	            		 int byteEnd   = line.indexOf(", idle_age");
	            		 String n_bytes = line.substring(byteStart, byteEnd);
	            		 
	            		 System.out.println(srcDstTag+" "+dstDcIndex+" "+n_bytes);
	            		 map.put(Integer.parseInt(dstDcIndex), n_bytes);
	            	}
	            }
	        }
	        String statMat = "";
	        for(int i=0; i<map.size(); i++){
	        	statMat = statMat + map.get(i) + " ";
	        }
	        System.out.println(statMat);
	        //this.socket.send("",0);
		}
	}
	
}
