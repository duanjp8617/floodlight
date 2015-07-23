package net.floodlightcontroller.nfvtest.message;

import net.floodlightcontroller.nfvtest.message.Message;

import java.util.ArrayList;

/*
 * Pending is used to record pending requests.
 * The cached message is used to cache an unreplied request.
 * 
 * original request
 * ----------------->
 *                     new request
 *                     ------------>
 *                     new reply
 *                     <------------
 *                     
 *                     new request
 *                     ------------>
 *                     new reply
 *                     <------------
 * original reply                    
 * <-----------------                    
 */

public class Pending {
	private final int pendingRepliesNum;
	private final ArrayList<Message> replyList;
	private final Message cachedMessage;
	
	public Pending(int pendingRepliesNum, Message cachedMessage){
		this.pendingRepliesNum = pendingRepliesNum;
		this.cachedMessage = cachedMessage;
		replyList = new ArrayList<Message>();
	}
	
	public Message getCachedMessage(){
		return this.cachedMessage;
	}
	
	public boolean addReply(Message reply){
		this.replyList.add(reply);
		if(this.replyList.size() == this.pendingRepliesNum){
			return true;
		}
		else{
			return false;
		}
	}
	
	public ArrayList<Message> getReplyList(){
		return this.replyList;
	}
}
