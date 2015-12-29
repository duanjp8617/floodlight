package localcontroller;

import org.zeromq.ZMQ;

public class Replier implements Runnable
{
	private ZMQ.Socket replier;
	private ZMQ.Socket requester;
	private ZMQ.Context zmqContext;
	private boolean bool = true;
	
	Replier(ZMQ.Context zmqContext)
	{
		this.zmqContext = zmqContext;
		
		this.init();
	}
	
	private void init()
	{
		replier = zmqContext.socket(ZMQ.REP);
		replier.bind("tcp://localhost:6000");
	}
	
	public void setRun(boolean bool)
	{
		this.bool = bool;
	}
	
	private void exit()
	{
		replier.close();
	}
	
	@Override
	public void run()
	{
		// TODO Auto-generated method stub
		while (bool)
		{
			replier.recv();
			replier.send("OK");
		}
		
		this.exit();
		
	}

}
