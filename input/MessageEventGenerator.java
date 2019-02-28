/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package input;

import core.Content;
import core.ContentType;
import core.DTNHost;
import java.util.Random;

import core.Settings;
import core.SettingsError;
import core.SimClock;
import core.World;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Message creation -external events generator. Creates uniformly distributed
 * message creation patterns whose message size and inter-message intervals can
 * be configured.
 */
public class MessageEventGenerator implements EventQueue {

    /**
     * Message size range -setting id ({@value}). Can be either a single value
     * or a range (min, max) of uniformly distributed random values. Defines the
     * message size (bytes).
     */
    public static final String MESSAGE_SIZE_S = "size";

    public static final String CONTENT_SIZE_S = "numOfContents";
    public static final String ZIPF_ALPHA = "zipfAlpha";

    public static final String MESSAGE_INTEREST_SIZE = "interestSize";
    /**
     * Message creation interval range -setting id ({@value}). Can be either a
     * single value or a range (min, max) of uniformly distributed random
     * values. Defines the inter-message creation interval (seconds).
     */
    public static final String MESSAGE_INTERVAL_S = "interval";
    /**
     * Sender/receiver address range -setting id ({@value}). The lower bound is
     * inclusive and upper bound exclusive.
     */
    public static final String HOST_RANGE_S = "hosts";
    /**
     * (Optional) receiver address range -setting id ({@value}). If a value for
     * this setting is defined, the destination hosts are selected from this
     * range and the source hosts from the {@link #HOST_RANGE_S} setting's
     * range. The lower bound is inclusive and upper bound exclusive.
     */
    public static final String TO_HOST_RANGE_S = "tohosts";
    public static final String PERCENT_SUBSCRIBER = "percentRandomSubscriber";
    public static final String PERCENT_PUBLISHER = "percentRandomPublisher";

    /**
     * Message ID prefix -setting id ({@value}). The value must be unique for
     * all message sources, so if you have more than one message generator, use
     * different prefix for all of them. The random number generator's seed is
     * derived from the prefix, so by changing the prefix, you'll get also a new
     * message sequence.
     */
    public static final String MESSAGE_ID_PREFIX_S = "prefix";
    /**
     * Message creation time range -setting id ({@value}). Defines the time
     * range when messages are created. No messages are created before the first
     * and after the second value. By default, messages are created for the
     * whole simulation time.
     */
    public static final String MESSAGE_TIME_S = "time";

    /**
     * Time of the next event (simulated seconds)
     */
    protected double nextEventsTime = 0;
    /**
     * Range of host addresses that can be senders or receivers
     */
    protected int[] hostRange = {0, 0};
    /**
     * Range of host addresses that can be receivers
     */
    protected int[] toHostRange = null;
    /**
     * Next identifier for a message
     */
    private int id = 0;
    /**
     * Prefix for the messages
     */
    protected String idPrefix;
    /**
     * Size range of the messages (min, max)
     */
    public static int[] sizeRange;
    private int[] interestSizeRange;
    /**
     * Interval between messages (min, max)
     */
    private int[] msgInterval;
    /**
     * Time range for message creation (min, max)
     */
    protected double[] msgTime;

    private double percentSubscriber = 0.0;
    private double percentPublisher = 0.0;
    private int[] arraySubscribers;
    public static int[] arrayPublishers;

    /**
     * Random number generator for this Class
     */
    public static ZipfGenerator zipf;
    public static int numOfContents;
    public double zipfAlpha;
    protected Random rng;

    /**
     * Constructor, initializes the interval between events, and the size of
     * messages generated, as well as number of hosts in the network.
     *
     * @param s Settings for this generator.
     */
    public MessageEventGenerator(Settings s) {
        this.sizeRange = s.getCsvInts(MESSAGE_SIZE_S);
        numOfContents = s.getInt(CONTENT_SIZE_S);
        this.interestSizeRange = s.getCsvInts(MESSAGE_INTEREST_SIZE);
        this.msgInterval = s.getCsvInts(MESSAGE_INTERVAL_S);
        this.hostRange = s.getCsvInts(HOST_RANGE_S, 2);
        this.idPrefix = s.getSetting(MESSAGE_ID_PREFIX_S);
        this.percentSubscriber = s.getDouble(PERCENT_SUBSCRIBER);
        this.percentPublisher = s.getDouble(PERCENT_PUBLISHER);
        this.zipfAlpha = s.getDouble(ZIPF_ALPHA);
        zipf = new ZipfGenerator(numOfContents, this.zipfAlpha); //size, skewness
        //zipf = new RealContentTracesGenerator(numOfContents);

        if (s.contains(MESSAGE_TIME_S)) {
            this.msgTime = s.getCsvDoubles(MESSAGE_TIME_S, 2);
        } else {
            this.msgTime = null;
        }
        if (s.contains(TO_HOST_RANGE_S)) {
            this.toHostRange = s.getCsvInts(TO_HOST_RANGE_S, 2);
        } else {
            this.toHostRange = null;
        }

        /* if prefix is unique, so will be the rng's sequence */
        this.rng = new Random(idPrefix.hashCode());

        if (this.sizeRange.length == 1) {
            /* convert single value to range with 0 length */
            this.sizeRange = new int[]{this.sizeRange[0], this.sizeRange[0]};
        } else {
            s.assertValidRange(this.sizeRange, MESSAGE_SIZE_S);
        }
        if (this.interestSizeRange.length == 1) {
            /* convert single value to range with 0 length */
            this.interestSizeRange = new int[]{this.interestSizeRange[0], this.interestSizeRange[0]};
        } else {
            s.assertValidRange(this.interestSizeRange, MESSAGE_INTEREST_SIZE);
        }
        if (this.msgInterval.length == 1) {
            this.msgInterval = new int[]{this.msgInterval[0],
                this.msgInterval[0]};
        } else {
            s.assertValidRange(this.msgInterval, MESSAGE_INTERVAL_S);
        }
        s.assertValidRange(this.hostRange, HOST_RANGE_S);

        if (this.hostRange[1] - this.hostRange[0] < 2) {
            if (this.toHostRange == null) {
                throw new SettingsError("Host range must contain at least two "
                        + "nodes unless toHostRange is defined");
            } else if (toHostRange[0] == this.hostRange[0]
                    && toHostRange[1] == this.hostRange[1]) {
                // XXX: teemuk: Since (X,X) == (X,X+1) in drawHostAddress()
                // there's still a boundary condition that can cause an
                // infinite loop.
                throw new SettingsError("If to and from host ranges contain"
                        + " only one host, they can't be the equal");
            }
        }

        /* calculate the first event's time */
        this.nextEventsTime = (this.msgTime != null ? this.msgTime[0] : 0)
                + msgInterval[0]
                + (msgInterval[0] == msgInterval[1] ? 0
                        : rng.nextInt(msgInterval[1] - msgInterval[0]));

        // Modified CODE BY VU SAN HA HUYNH
        arraySubscribers = new int[(int) ((hostRange[1] - hostRange[0]) * percentSubscriber)];
        for (int i = 0; i < arraySubscribers.length; i++) {
            boolean isContainedIn = false;
            int nextInt = hostRange[0] + rng.nextInt(hostRange[1] - hostRange[0]);
            for (int j = 0; j < i; j++) {

                if (arraySubscribers[j] == nextInt) {
                    isContainedIn = true;
                }
            }
            if (!isContainedIn) {
                arraySubscribers[i] = nextInt;
            } else {
                i--;
            }
        }
        //arraySubscribers = new int[]{79,78,21,34,3,55,48,10,59,56}; // Total Best
        //arraySubscribers = new int[]{99,13,22,94,90,93,53,96,39,92,97}; // Total Worst
        //arraySubscribers = new int[]{79,21,3,48,59,66,28,41,70,73}; // Best
        //arraySubscribers = new int[]{99,19,9,89,95,20,22,90,53,39}; // Worst

        
        // San Francisco Cab
        //arraySubscribers = new int[]{97, 92, 39, 96, 53, 93, 90, 94, 22, 13, 99, 20, 91, 95, 71, 89, 98, 9, 88, 19, 83, 18, 38, 49, 24, 86, 85, 47, 30, 29, 43, 87, 52, 33, 2, 11, 54,
        //    44, 81, 74, 76, 7, 72, 67, 37, 84, 50, 46, 82, 8, 65, 35, 6, 77, 60, 68, 15, 12, 63, 69, 45, 25, 17, 80, 75, 64, 51, 0, 36, 4, 58, 27, 42, 40, 1, 32, 23, 14, 62, 31, 73, 61, 70, 5, 41, 26, 28, 57, 66, 16,
        //    };
      
        // RollerNet
        //arraySubscribers = new int[]{23,17,61,56,26,27,12,3,19,60,59,9,22,5,51,41,24,21,11,35,13,4,58,20,54,42,55,30,7,34,6,10,2,32,52,46,14,18,40,53,57,16,
        //0,36,39,15,50,43,31,37,45,25,29,48,38,1};
      
        // Infocom
        arraySubscribers = new int[]{75,54,40,32,29,43,4,13,39,3,73,93,23,77,36,14,1,37,19,16,96,0,12,15,76,34,38,26,82,59,56,50,65,42,83,62,60,
        45,35,47,22,51,97,41,31,87,78,58,52,53,21,55,24,95,71,86,88,27,80,25,46,30,91,69,67,84,68,61,74,64,94,44,72,
        63,89,70,20,66,79,85,92,90};
        
        // Reverse
        for (int i = 0; i < arraySubscribers.length / 2; i++) {
            int temp = arraySubscribers[i];
            arraySubscribers[i] = arraySubscribers[arraySubscribers.length - i - 1];
            arraySubscribers[arraySubscribers.length - i - 1] = temp;
        }
        
        
        int[] tmp = Arrays.copyOf(arraySubscribers, (int) (arraySubscribers.length * this.percentSubscriber));
        arraySubscribers = tmp;
        

        //int[]
        tmp = new int[hostRange[1] - hostRange[0]];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = hostRange[0] + i;
        }
        arrayPublishers = new int[tmp.length - arraySubscribers.length];
        int count = 0;
        for (int i = 0; i < tmp.length; i++) {
            boolean isContainedIn = false;
            for (int j = 0; j < arraySubscribers.length; j++) {
                if (arraySubscribers[j] == hostRange[0] + i) {
                    isContainedIn = true;
                }
            }
            if (!isContainedIn) {
                arrayPublishers[count] = hostRange[0] + i;
                count++;
            }
        }
        tmp = Arrays.copyOf(arrayPublishers, (int) (arrayPublishers.length * this.percentPublisher));
        arrayPublishers = tmp;
        //arrayPublishers = new int[]{78,34,55,10,56,57,26,5,61,16}; // Best
        //arrayPublishers = new int[]{13,88,98,71,91,99,94,93,96,92,97}; // Worst
        
        // San Francisco Cab
        //arrayPublishers = new int[]{79, 78, 21, 34, 3, 55, 48, 10, 59, 56};
     
        // RollerNet
        //arrayPublishers = new int[]{33,44,8,28,49,47};
        
        // Infocom
        arrayPublishers = new int[]{7,81,28,57,48,17,33,2,49,18,};

    }

    /**
     * Draws a random host address from the configured address range
     *
     * @param hostRange The range of hosts
     * @return A random host address
     */
    protected int drawHostAddress(int hostRange[]) {
        /* if (hostRange[1] == hostRange[0]) {
			return hostRange[0];
		}
		return hostRange[0] + rng.nextInt(hostRange[1] - hostRange[0]); */

        // Modified CODE BY VU SAN HA HUYNH
        return arraySubscribers[new Random().nextInt(arraySubscribers.length)];
    }

    /**
     * Generates a (random) message size
     *
     * @return message size
     */
    protected int drawMessageSize() {
        int sizeDiff = sizeRange[0] == sizeRange[1] ? 0
                : rng.nextInt(sizeRange[1] - sizeRange[0]);
        return sizeRange[0] + sizeDiff;
    }

    protected int drawInterestMessageSize() {
        int sizeDiff = interestSizeRange[0] == interestSizeRange[1] ? 0
                : rng.nextInt(interestSizeRange[1] - interestSizeRange[0]);
        return interestSizeRange[0] + sizeDiff;
    }

    /**
     * Generates a (random) time difference between two events
     *
     * @return the time difference
     */
    protected int drawNextEventTimeDiff() {
        int timeDiff = msgInterval[0] == msgInterval[1] ? 0
                : rng.nextInt(msgInterval[1] - msgInterval[0]);
        return msgInterval[0] + timeDiff;
    }

    /**
     * Draws a destination host address that is different from the "from"
     * address
     *
     * @param hostRange The range of hosts
     * @param from the "from" address
     * @return a destination address from the range, but different from "from"
     */
    protected int drawToAddress(int hostRange[], int from) {
        /* int to;
		do {
			to = this.toHostRange != null ? drawHostAddress(this.toHostRange):
				drawHostAddress(this.hostRange);
		} while (from==to);

		return to; */
        return arrayPublishers[new Random().nextInt(arrayPublishers.length)];
    }

    /**
     * Returns the next message creation event
     *
     * @see input.EventQueue#nextEvent()
     */
    public ExternalEvent nextEvent() {
        int responseSize = 0;
        /* zero stands for one way messages */
        int msgSize;
        int interval;
        int from;
        int to;

        /* Get two *different* nodes randomly from the host ranges */
        from = drawHostAddress(this.hostRange);
        to = drawToAddress(hostRange, from);
        //System.out.println(from + " --> " + to);

        msgSize = drawMessageSize();
        interval = drawNextEventTimeDiff();

        /* Create event and advance to next event */
        MessageCreateEvent mce = new MessageCreateEvent(from, to, this.getID(),
                msgSize, responseSize, this.nextEventsTime, drawInterestMessageSize());
        this.nextEventsTime += interval;

        if (this.msgTime != null && this.nextEventsTime > this.msgTime[1]) {
            /* next event would be later than the end time */
            this.nextEventsTime = Double.MAX_VALUE;
        }

        return mce;
    }

    /**
     * Returns next message creation event's time
     *
     * @see input.EventQueue#nextEventsTime()
     */
    public double nextEventsTime() {
        return this.nextEventsTime;
    }

    /**
     * Returns a next free message ID
     *
     * @return next globally unique message ID
     */
    protected String getID() {
        this.id++;
        return idPrefix + this.id;
    }
}
