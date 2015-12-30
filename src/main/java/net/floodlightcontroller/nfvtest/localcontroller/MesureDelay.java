package localcontroller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZStar.Set;

public class MesureDelay implements Runnable
{
	private final int id;
	private final int interval;
	private final Map<Integer, String> map;
	private Map<Integer, Integer> delay;
	
	private ZMQ.Socket requester;
	private ZMQ.Context zmqContext;
	
	private Replier replier;
	
	private boolean bool;
	
	
	MesureDelay(int id, int interval, Map<Integer, String> map)
	{
		this.id = id;
		this.interval = interval;
		this.map = map;
		this.delay = new HashMap<Integer, Integer>();
		this.bool = true;
		this.lock = new Object();
		
		init();
	}
	
	private void init()
	{
		//create socket to reply other local controllers
		zmqContext = ZMQ.context(1);
	    replier = new Replier(zmqContext);
		Thread th = new Thread(replier);
		th.start();
	}
	
	private void exit()
	{
		replier.setRun(false);
		zmqContext.term();
	}
	
	public void setRun(boolean bool)
	{
		this.bool = bool;
	}
	
	private void measure()
	{
		this.delay.clear();
		Set set = (Set) map.keySet();
		Iterator it = ((ArrayList) set).iterator();
		while(it.hasNext())
		{
			int key = (int) it.next();
			if (key <= this.id)
			{
				continue;
			}
			String remoteIp = (String) map.get(key);
			
			requester = zmqContext.socket(ZMQ.REQ);
			requester.monitor("inproc://monitorServerConnection", ZMQ.EVENT_CONNECTED);
			
			Socket monitor = zmqContext.socket(ZMQ.PAIR);
			monitor.setReceiveTimeOut(5000);
			monitor.connect("inproc://monitorServerConnection");
			
			requester.connect("tcp://"+ remoteIp + ":6000");
			
			while(ZMQ.Event.recv(monitor) == null || ZMQ.Event.recv(monitor).getEvent() != ZMQ.EVENT_CONNECTED)
			{
				requester.connect("tcp://"+ remoteIp + ":6000");
			}
			monitor.close();
			
			//start to measure
			long startTime = System.nanoTime();
			requester.send("Hello!");
			requester.recv();
			int consumingTime = (int) ((System.nanoTime() - startTime)/1000);
			
			this.delay.put(key, consumingTime);
			
			requester.close();
		}
		
	}

	
	public Map<Integer, Integer> getDelay()
	{
		return this.delay;
	}
	
	@Override
	public void run()
	{
		// TODO Auto-generated method stub
		while (bool)
		{
			synchronized(this)
			{
				measure();
			}
			try
			{
				Thread.sleep(this.interval);
			} catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		this.exit();
	}

}
