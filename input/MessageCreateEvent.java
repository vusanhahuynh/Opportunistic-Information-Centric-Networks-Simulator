/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package input;

import core.Content;
import core.ContentType;
import core.DTNHost;
import core.Message;
import core.World;
import core.DTNSim;
import core.SimClock;
import java.util.Random;

/**
 * External event for creating a message.
 */
public class MessageCreateEvent extends MessageEvent {

    private int size;
    private int responseSize;
    private int interestSize;

    /**
     * Creates a message creation event with a optional response request
     *
     * @param from The creator of the message
     * @param to Where the message is destined to
     * @param id ID of the message
     * @param size Size of the message
     * @param responseSize Size of the requested response message or 0 if no
     * response is requested
     * @param time Time, when the message is created
     * @param interestSize
     */
    public MessageCreateEvent(int from, int to, String id, int size,
            int responseSize, double time, int interestSize) {
        super(from, to, id, time);
        this.size = size;
        this.responseSize = responseSize;
        this.interestSize = interestSize;
    }

    /**
     * Creates the message this event represents.
     */
    @Override
    public void processEvent(World world) {
        DTNHost to = world.getNodeByAddress(this.toAddr);
        DTNHost from = world.getNodeByAddress(this.fromAddr);
        /*Message m = new Message(from, to, this.id, this.size,null);
	m.setResponseSize(this.responseSize);
	from.createNewMessage(m);*/
        
        // Modified CODE BY VU SAN HA HUYNH
        // Assume interest requested and content published at the same time, destination does not matter (unknonwn in realistic)
        // Interest - Type: 0
        int zipfNumber = MessageEventGenerator.zipf.next();
        Content content = new Content(zipfNumber, ContentType.Interest, SimClock.getTime(), 0, interestSize);
        Message m1 = new Message(from, null, this.id + "_interest", interestSize, content);

        m1.setResponseSize(this.responseSize);
        from.createNewMessage(m1);

        // Advertisement - Type: 1
        /*Random rand = new Random();
                content = new Content(zipfNumber, ContentType.Advert, SimClock.getTime(), 0);
		Message m2 = new Message(to, null, this.id + "_advert", this.size, content);
                
		m2.setResponseSize(this.responseSize);
		from.createNewMessage(m2);*/
        //System.out.println(from + ": " + this.id + "  " + to + ": " + (this.id + 1000));
        
        DTNHost tmpTo = world.getHostContainsContent(content.getContentId());
        // Advertisement - Type: 1
        /*Random rand = new Random();
                content = new Content(zipfNumber, ContentType.Advert, SimClock.getTime(), 0);*/
        Content c = new Content(content.getContentId(), ContentType.Advert, SimClock.getTime(), (double) 3600, this.size);
        Message m2 = new Message(tmpTo, null, this.id + "_advert", this.size, c);

        m2.setResponseSize(this.responseSize);
        tmpTo.createNewMessage(m2);
    }

    @Override
    public String toString() {
        return super.toString() + " [" + fromAddr + "->" + toAddr + "] "
                + "size:" + size + " CREATE";
    }
}
