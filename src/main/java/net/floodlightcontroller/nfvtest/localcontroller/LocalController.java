package localcontroller;

import java.util.concurrent.LinkedBlockingQueue;
import org.zeromq.ZMQ;
import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.GlobScaleRequest;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.GlobScaleReply;


public class LocalController extends MessageProcessor
{
	
	private final int DC_NUM;
	private final int CTRL_CHAIN_LEN;
	private final int DATA_CHAIN_LEN;
	
	//send to global controller
	private int delay[];
	private int networkDeployment[];
	
	//receive from global controller
	private int ctrlProvision[][];
	private int dataProvision[][];
	private int networkTopo[][][];
	
	private ZMQ.Socket sock;
	private ZMQ.Context zmqContext;
	
//	private final int id;  //each local controller (data center) has a unique identifier, which equals to port-5555;
	
	LocalController(int dcNum, int ctrlChainLen, int dataChainLen, int id)
	{
		this.DC_NUM = dcNum;
		this.CTRL_CHAIN_LEN = ctrlChainLen;
		this.DATA_CHAIN_LEN = dataChainLen;
//		this.id = id;
		
		this.ctrlProvision = new int[dcNum][ctrlChainLen];
		this.dataProvision = new int[dcNum][dataChainLen];
		this.networkTopo = new int[dcNum][dcNum][dataChainLen];
		
		this.init();
		
	}
	
	public int[][] getCtrlProvision()
	{
		return this.ctrlProvision;
	}
	
	public int[][] getDataProvision()
	{
		return this.dataProvision;
	}
	
	public int[][][] getNetworkTopo()
	{
		return this.networkTopo;
	}
	
	private void setDelay()
	{
		//this.delay = {{0}};
		this.delay = new int[DC_NUM];
		for (int i=0; i<DC_NUM; i++)
		{
			delay[i] = 0;
		}
	}
	
	private void setNetworkDeployment()
	{
		//this.networkDeployment = networkDeployment;
		this.networkDeployment = new int[DATA_CHAIN_LEN];
		for (int i=0; i<DATA_CHAIN_LEN; i++)
		{
			networkDeployment[i] = 3;
		}
	}
	
	
	//create socket to connect to global controller
	private void init()
	{
		//create socket to connect to global controller
		zmqContext = ZMQ.context(1);
		sock = zmqContext.socket(ZMQ.REP);
		sock.connect("tcp://localhost:5555");
	}
	
	
	public void exit()
	{
		sock.close();
		zmqContext.term();
	}
	
	
	//run method
	public void connToLocalController()
	{
		
		while (true)
		{
			byte[] recvArr = sock.recv(0);
			String recvStr = new String(recvArr);
			System.out.println("recvStr " + recvStr);
			
//			if (recvStr.equals("HELLO"))
//			{
//				this.sendID();
//			}
			if (recvStr.equals("START"))
			{
				//send delay and networkDeployment to global controller
				this.sendInfoToGlob();
			}
			else if (recvStr.equals("RESULT"))
			{
				sock.send("OKAY".getBytes(), 0);
				
				//receive control plane provision, data plane provision and path
				this.recvResult();
			}
			else if (recvStr.equals("SCALE"))
			{
				//start to scale according to provisioning
				this.handleScale();
			}
			else if (recvStr.equals("PATH"))
			{
				//adjust path of each service chain if necessary
				sock.send("OKAY".getBytes(), 0);
			}
//			else if (recvStr.equals("BYE"))
//			{
//				//exit
//				sock.send("BYE");   
//			}
		}

	}
	
	
	//reply HELLO
//	private void sendID()
//	{
//		byte sendArrByte[] = new byte[4];
//		sendArrByte[0] = (byte)(this.id>>24 & 0xff);
//		sendArrByte[1] = (byte)(this.id>>16 & 0xff);
//		sendArrByte[2] = (byte)(this.id>>8 & 0xff);
//		sendArrByte[3] = (byte)(this.id& 0xff);
//		
//		sock.send(sendArrByte);   //reply "HELLO"
//		
//	}
//	
	
	
	private void sendInfoToGlob()
	{
		/*need to first get delay and traffic first from other classes by method call
		*local controller need to provide such functions
		*setDelay and setTraffic should be called by this class self, not by external class
		*/

		this.setDelay();
		this.setNetworkDeployment();
		
		//data is ready
		int sendArr[] = new int[DC_NUM+DATA_CHAIN_LEN];
		int length = 0;
		
		for (int i=0; i<DATA_CHAIN_LEN; i++)
		{
			sendArr[length] = networkDeployment[i];
			length++;
		}
		
		for (int i=0; i<DC_NUM; i++)
		{
			sendArr[length] = delay[i];
			length++;
		}
		
		//int array to byte array
		byte sendArrByte[] = new byte[length*4];
		for (int i=0; i<length; i++)
		{
			sendArrByte[4*i] = (byte)(sendArr[i]>>24 & 0xff);
			sendArrByte[4*i+1] = (byte)(sendArr[i]>>16 & 0xff);
			sendArrByte[4*i+2] = (byte)(sendArr[i]>>8 & 0xff);
			sendArrByte[4*i+3] = (byte)(sendArr[i] & 0xff);
		}
		
		sock.send(sendArrByte);
	}
	
	
	private void recvResult()
	{
		byte recvArrByte[] = sock.recv(0);
		
		//byte to int
		int recvArr[] = new int[recvArrByte.length/4];
		for (int j=0; j<recvArr.length; j++)
		{
			recvArr[j] = recvArrByte[4*j+3] & 0xFF |   (recvArrByte[4*j+2] & 0xFF) << 8 | (recvArrByte[4*j+1] & 0xFF) << 16 |  (recvArrByte[4*j] & 0xFF) << 24;
		}
		
		int length = 0;
		//recvArr to ctrlProvision, dataProvision, networkTopo
		for (int i=0; i<DC_NUM; i++)
		{
			for (int j=0; j<CTRL_CHAIN_LEN; j++)
			{
				this.ctrlProvision[i][j] = recvArr[length];
				length++;
			}
		}
		
		for (int i=0; i<DC_NUM; i++)
		{
			for (int j=0; j<DATA_CHAIN_LEN; j++)
			{
				this.dataProvision[i][j] = recvArr[length];
				length++;
			}
		}
		
		
		for (int i=0; i<DC_NUM; i++)
		{
			for (int j=0; j<DC_NUM; j++)
			{
				for (int k=0; k<DATA_CHAIN_LEN; k++)
				{
					this.networkTopo[i][j][k] = recvArr[length];
					length++;
				}
			}
		}
		
		
		
		sock.send("OKAY".getBytes(), 0);
	}

	
	//send scaling request to chainHandler and wait for reply
	private void handleScale()
	{
		//send scale control plane request
		GlobScaleRequest ctrlRequest = new GlobScaleRequest("local controller interface", this.ctrlProvision, true);
		this.mh.sendTo("chainHandler", ctrlRequest);   //? is it sent to chainHandler?
		LinkedBlockingQueue<Message> q = (LinkedBlockingQueue<Message>)this.queue;
		while (true)
		{
			try
			{
				Message m = q.take();
				if(m instanceof GlobScaleReply)
				{
					GlobScaleReply reply = (GlobScaleReply)m;
					if (reply.getSuccess() == true)
					{
						break;
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}	

		}
		
		//send scale data plane request
		GlobScaleRequest dataRequest = new GlobScaleRequest("local controller interface", this.dataProvision, false);
		this.mh.sendTo("chainHandler", dataRequest);   //? is it sent to chainHandler?
		while (true)
		{
			try
			{
				Message m = q.take();
				if(m instanceof GlobScaleReply)
				{
					GlobScaleReply reply = (GlobScaleReply)m;
					if (reply.getSuccess() == true)
					{
						break;
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}	

		}

		//scaling successfully
		sock.send("OKAY".getBytes(), 0);
	}
	
	
	
	protected void onReceive(Message m)
	{
		
	}
	
	
	
	public void run()
	{
		
	}


}



