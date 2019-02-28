/*
 * EBRRouter.java
 *
 * Created on February 6, 2008, 10:07 AM
 * From the paper "Encounter-based Routing in DTNs" found in
 * IEEE INFOCOM 2009
 *
 * @author Sam Nelson
 */

package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import util.Tuple;

/**
 * Route based on encounter value
 */
public class EBRRouter extends ActiveRouter {
	
	/** identifier for the intial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";
	public static final String ALPHA = "alpha";
	public static final String UPDATE_POPINTERVAL = "updatePOPInterval";
	/** Popularity router's settings namespace ({@value})*/ 
	public static final String EBR_NS = "EBRRouter";    
	public static final String MSG_COUNT_PROPERTY = EBR_NS + "." + "copies";

	/** Popularity counter */
	private double EV = 0;
	/** Current window counter */
	private double windowCounter = 0;
	/** Alpha */
	private double alpha;
	/** Initial number of copies */
	private int initialNrofCopies;
	/** Future time to update EV */
	private double updateInterval;
	private double timeToUpdate = 0;

	/** IDs of the messages that are known to have reached the final dst */
	private Set<String> ackedMessageIds;

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public EBRRouter(Settings s) {
		super(s);
		Settings EBRSettings = new Settings(EBR_NS);
		initialNrofCopies = EBRSettings.getInt(NROF_COPIES);
		alpha = EBRSettings.getDouble(ALPHA);
		updateInterval = EBRSettings.getInt(UPDATE_POPINTERVAL);
	}

	/**
	 * Copyconstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected EBRRouter(EBRRouter r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		this.alpha = r.alpha;
		this.updateInterval = r.updateInterval;
		this.ackedMessageIds = new HashSet<String>();
		this.EV = r.EV;
		this.windowCounter = r.windowCounter;
	}

	@Override
	public int receiveMessage(Message m, DTNHost from) {
		return super.receiveMessage(m, from);
	}

	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {

			DTNHost otherHost = con.getOtherNode(getHost());
			MessageRouter mRouter = otherHost.getRouter();

			assert mRouter instanceof EBRRouter : "EBRRouter only works "+ 
			" with other routers of same type";
			EBRRouter otherRouter = (EBRRouter)mRouter;

			// exchange ACKed message data
			this.ackedMessageIds.addAll(otherRouter.ackedMessageIds);
			otherRouter.ackedMessageIds.addAll(this.ackedMessageIds);
			deleteAckedMessages();
			otherRouter.deleteAckedMessages();

			// Update current window counter
			windowCounter++;

		}
	}

	public double getEV() {
		return this.EV;
	}

	/**
	 * Deletes the messages from the message buffer that are known to be ACKed
	 */
	private void deleteAckedMessages() {
		for (String id : this.ackedMessageIds) {
			if (this.hasMessage(id)) {
				this.deleteMessage(id, false);
			}
		}
	}

	@Override 
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());	
		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(msg, true);				
		return true;
	}

	@Override
	public void update() {
		super.update();
		// Update popularity counter if need be
		if (SimClock.getTime() >= timeToUpdate) {
			EV = alpha * windowCounter + (1-alpha) * EV;
			windowCounter = 0;
			timeToUpdate = SimClock.getTime() + updateInterval;
		}

		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}

		tryOtherMessages();

	}

	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 

		Collection<Message> msgCollection = getMessageCollection();

		// for all connected hosts collect all messages that have a higher
		// probability of delivery by the other host
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			EBRRouter othRouter = (EBRRouter)other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}

			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}

				/** Assume our popularity is x and the other router's
				 *  popularity is y.  Let n be the number of copies of
				 *  the message we have.  Then we want to transfer
				 *  floor( y/x+y  *  n) messages to the other router
				 */;
				 double y = othRouter.getEV();
				 double x = this.getEV();
				 int n = ((Integer)m.getProperty(MSG_COUNT_PROPERTY)).intValue();

				 /** The other nodes popularity is high enough to send message */
				 if (Math.floor((y*n)/(x+y)) > 1) {
					 messages.add(new Tuple<Message, Connection>(m,con));
				 }

			}			
		}

		if (messages.size() == 0) {
			return null;
		}

		// sort the message-connection tuples
		Collections.sort(messages, new TupleComparator());
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	/**
	 * Comparator for Message-Connection-Tuples that orders the tuples by
	 * their connection's popularity
	 */
	private class TupleComparator implements Comparator 
	<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			// popularity counter for the connection in tuple1
			double p1 = ((EBRRouter)tuple1.getValue().
					getOtherNode(getHost()).getRouter()).
					getEV();
			// tuple2
			double p2 = ((EBRRouter)tuple2.getValue().
					getOtherNode(getHost()).getRouter()).
					getEV();

			// bigger popularity comes first
			if (p2-p1 == 0) {
				return 0;
			}
			else if (p2-p1 < 0) {
				return -1;
			}
			else {
				return 1;
			}
		}
	}

	/**
	 * Reduces the number of copies we have left for a message.
	 * This is the SENDER's copy
	 */
	@Override
	protected void transferDone(Connection con) {
		String msgId = con.getMessage().getId();

		// Get this router's copy of the message
		Message msg = getMessage(msgId);

		/** If destination was reached, delete all copies from sender */
		if (con.getMessage().getTo().equals(con.getOtherNode(getHost()))) {
			this.ackedMessageIds.add(msgId);
			this.deleteMessage(msgId,false);
			return;
		}

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}

		// Get nodes' EVs
		double x = this.getEV();
		double y = ((EBRRouter)con.getOtherNode(getHost()).getRouter()).getEV();
		int n = ((Integer)msg.getProperty(MSG_COUNT_PROPERTY)).intValue();

		// Transfered floor( y/(x+y) * n), and kept rest for self
		int newCount = n - (int)Math.floor((y*n)/(x+y));
		msg.updateProperty(MSG_COUNT_PROPERTY, new Integer(newCount));

	}

	/**
	 * This is the RECEIVER'S copy
	 */
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);

		// If this is final dest, simply set num of copies to 1
		if (msg.getTo().equals(this.getHost())) {
			msg.updateProperty(MSG_COUNT_PROPERTY, new Integer(1));
			this.ackedMessageIds.add(id);
			return msg;
		}

		double y = this.getEV();
		double x = ((EBRRouter)from.getRouter()).getEV();
		int n = ((Integer)msg.getProperty(MSG_COUNT_PROPERTY)).intValue();

		// "from" transfered floor( y/(x+y) * n) to self
		int newCount = (int)Math.floor((y*n)/(x+y));
		msg.updateProperty(MSG_COUNT_PROPERTY, new Integer(newCount));

		return msg;
	}

	@Override
	public EBRRouter replicate() {
		return new EBRRouter(this);
	}

}