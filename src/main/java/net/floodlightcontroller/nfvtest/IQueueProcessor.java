package net.floodlightcontroller.nfvtest;
import java.util.concurrent.ConcurrentLinkedQueue;

public interface IQueueProcessor {
	void processQueue(ConcurrentLinkedQueue<Message> queue);
}