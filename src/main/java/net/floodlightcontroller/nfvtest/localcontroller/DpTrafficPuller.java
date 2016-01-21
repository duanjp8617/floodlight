package net.floodlightcontroller.nfvtest.localcontroller;

import java.io.*;

public class DpTrafficPuller implements Runnable{
	private int pullInterval;
	private volatile boolean quit;
	
	public DpTrafficPuller(int pullInterval){
		this.pullInterval = pullInterval;
		this.quit = false;
	}
	
	public void quit(){
		this.quit = true;
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
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
	        String line = null;;
	        while (true) {
	            try {
					line = r.readLine();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	            if (line == null) { break; }
	            System.out.println(line);
	        }
		}
	}
	
}
