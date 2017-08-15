package uk.ac.ebi.biosamples.messages.threaded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class MessageBuffer<T,S> {
	private Logger log = LoggerFactory.getLogger(this.getClass());

	// TODO
	// 1. Turn the message status queue into a CuncurrentHashMap<Accession, MeesageSampleStatus<S>>
//    private final BlockingQueue<MessageSampleStatus<S>> messageSampleStatusQueue;
	private final ConcurrentMap<T, MessageSampleStatus<S>> messageSampleStatusMap;
    private final AtomicLong latestTime;
    public final AtomicBoolean hadProblem = new AtomicBoolean(false);
 
    private final int queueSize;
    private final int queueTime;
	
	public MessageBuffer(int queueSize, int queueTime) {
		this.queueSize = queueSize;
		this.queueTime = queueTime;
		messageSampleStatusMap = new ConcurrentHashMap<>();
		latestTime = new AtomicLong(0);
	}
	
	public MessageSampleStatus<S> receive(T key, S sample) throws InterruptedException {
		//if there is no maximum time
		//set it to wait in the future
		latestTime.compareAndSet(0, Instant.now().toEpochMilli()+queueTime);
		
		MessageSampleStatus<S> status = MessageSampleStatus.build(sample);
		
		//this will block until space is available
		MessageSampleStatus<S> oldValue = null;
		boolean done = false;
		while (!done) {
			synchronized (messageSampleStatusMap) {
				if (messageSampleStatusMap.size() < queueSize) {
					oldValue = messageSampleStatusMap.put(key, status);
					done = true;
				}
			}
			Thread.sleep(10);
		}
		if (oldValue != null) {
			oldValue.storedInRepository.set(true);
		}
		return status;
	}
	
	public boolean areAllStored() {
		for (MessageSampleStatus<S> messageSampleStatus : messageSampleStatusMap.values()) {
			if (!messageSampleStatus.storedInRepository.get()) {
				return false;
			}
		}
		return true;
	}
	
	@Scheduled(fixedDelay = 100)
	public void checkQueueStatus() {
		//check if enough time has elapsed
		//or if the queue is long enough
		synchronized (messageSampleStatusMap) {
            int remaining = queueSize - messageSampleStatusMap.size();
            long now = Instant.now().toEpochMilli();
            long latestTimeLong = latestTime.get();
            log.trace(""+remaining+" queue spaces and now "+now+" vs "+latestTimeLong);
            if (remaining <= 0.01*queueSize
                    || (now > latestTimeLong && latestTimeLong != 0)) {

				if (remaining <= 0.01 * queueSize) {
					log.info("Committing queue because full");
				}
				if (now > latestTimeLong && latestTimeLong != 0) {
					log.info("Committing queue because old");
				}


				//create a local collection of the messages
//				List<MessageSampleStatus<S>> messageSampleStatuses = new ArrayList<>(queueSize);

				try {
					//unset the latest time so that it can be set again by the next message
					latestTime.set(0);

					//drain the master queue into it
//					messageSampleStatusQueue.drainTo(messageSampleStatuses, queueSize);

					//now we can process the local copy without worrying about new ones being added

					//split out the samples into a separate list
					List<S> samples = new ArrayList<>();
					messageSampleStatusMap.values().forEach(m -> samples.add(m.sample));

					//send everything as a single transaction
					save(samples);
					//this was a hard commit so they are now written to disk

					//if there was a problem, an exception would be thrown
					//since we are still here, no unrecoverable problems occurred

					//mark each of the status as completed
					//this will trigger the waiting threads to continue
					messageSampleStatusMap.values().forEach(m -> m.storedInRepository.set(true));
					messageSampleStatusMap.clear();
				} catch (RuntimeException e) {
					//store that we encountered a problem of some kind
					log.error("Storing warning", e);
					hadProblem.set(true);
					messageSampleStatusMap.values().forEach(m -> m.hadProblem.set(e, true));
					//re-throw the exception
					throw e;
				}
			}
		}
	}
	/**
	 * This is the method that a specific sub-class should implement. Typically
	 * this will be some sort of repository.save(samples) call in a transaction
	 * 	
	 * @param repository
	 * @param samples
	 */
	public abstract void save(Collection<S> samples);
}
