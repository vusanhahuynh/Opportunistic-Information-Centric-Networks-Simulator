package routing;

import java.util.*;
import core.*;
import util.Tuple;

// import core.SimClock;
/**
 * An implementation of CAFE DTN routing
 */
public class CafeRouter extends ActiveRouter {

    /**
     * Cafe router's settings name space ({@value})
     */
    public static final String Cafe_NS = "CafeRouter";
    /**
     * identifier for the initial number of copies setting ({@value})
     */
    public static final String NROF_COPIES_S = "nrofCopies";
    /**
     * identifier for the difference in timer values needed to forward on a
     * message copy
     */
    public static final String TIMER_THRESHOLD_S = "transitivityTimerThreshold";
    /**
     * Message property key for the remaining available copies of a message
     */
    public static final String MSG_COUNT_PROP = "Cafe.copies";
    /**
     * Message property key for summary vector messages exchanged between direct
     * peers
     */
    public static final String SUMMARY_XCHG_PROP = "HelloMessage.protoXchg";
    public static final String AVGTIMEBUFFERFREE_XCHG_PROP = "Cafe.avgTimeBufferFree";
    public static final String PRCTIMEBUFFERFULL_XCHG_PROP = "Cafe.prcTimeBufferFull";
    public static final String AVGMSGDELAY_XCHG_PROP = "Cafe.avgMsgDelay";
    public static final String EGOAVGTIMEBUFFERFREE_XCHG_PROP = "Cafe.EgoavgTimeBufferFree";
    public static final String EGOPRCTIMEBUFFERFULL_XCHG_PROP = "Cafe.EgoprcTimeBufferFull";
    public static final String EGOAVGMSGDELAY_XCHG_PROP = "Cafe.EgoavgMsgDelay";
    public static final String EBC_XCHG_PROP = "HelloMessage.ebcXchg";
    public static final String Fn_XCHG_PROP = "HelloMessage.FnXchg";
    public static final String Dn_XCHG_PROP = "HelloMessage.DnXchg";
    public static final String L_XCHG_PROP = "HelloMessage.LXchg";
    protected static final String SUMMARY_XCHG_IDPREFIX = "summary";
    protected static final double defaultTransitivityThreshold = 60.0;
    protected static int protocolMsgIdx = 0;
    protected int initialNrofCopies;
    protected double transitivityTimerThreshold;
    /**
     * Stores information about nodes with which this host has come in contact
     */
    protected Map<DTNHost, EncounterInfo> recentEncounters;
    protected Map<DTNHost, Map<DTNHost, EncounterInfo>> neighborEncounters;
    // 
    protected long TimeBufferFull;
    protected long NumberOfTimesBufferFree;
    protected boolean BufferFullNow;
    protected long LengthBufferFree;
    protected double PercentBufferAvailable;
    protected double EgoNetPercentBufferAvailable;
    protected long AverageLengthTimeBufferFree;
    protected double PercentTimeBufferFull;
    protected int AverageMessageDelay;
    protected long EgoNetAverageLengthTimeBufferFree;
    protected double EgoNetPercentTimeBufferFull;
    protected int EgoNetAverageMessageDelay;
    // Ego Betweenness Centrality
    protected double EBC;
    // F(n) = total contact frequency
    protected int Fn;
    // D(n) = total contact duration
    protected double Dn;
    // Time of Creation
    protected double TOC;

    public CafeRouter(Settings s) {
        super(s);
        Settings cafe = new Settings(Cafe_NS);
        initialNrofCopies = cafe.getInt(NROF_COPIES_S);

        if (cafe.contains(TIMER_THRESHOLD_S)) {
            transitivityTimerThreshold = cafe.getDouble(TIMER_THRESHOLD_S);
        } else {
            transitivityTimerThreshold = defaultTransitivityThreshold;
        }

        recentEncounters = new HashMap<DTNHost, EncounterInfo>();
        neighborEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();

        // 
        TimeBufferFull = 0;
        NumberOfTimesBufferFree = 0;
        BufferFullNow = false;
        LengthBufferFree = 0;
        AverageLengthTimeBufferFree = 0;
        PercentTimeBufferFull = 0;
        AverageMessageDelay = 0;

        PercentBufferAvailable = 100.0;
        EgoNetPercentBufferAvailable = 100.0;

        EgoNetAverageLengthTimeBufferFree = 0;
        EgoNetPercentTimeBufferFull = 0;
        EgoNetAverageMessageDelay = 0;

        EBC = 0.0;
        Fn = 0;
        Dn = 0.0;
        TOC = SimClock.getTime();
    }

    /**
     * Copy Constructor.
     *
     * @param r The router from which settings should be copied
     */
    public CafeRouter(CafeRouter r) {
        super(r);
        this.initialNrofCopies = r.initialNrofCopies;

        recentEncounters = new HashMap<DTNHost, EncounterInfo>();
        neighborEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();

        TimeBufferFull = 0; // or should it be r.percenTimeBufferFull;
        NumberOfTimesBufferFree = 0; // or shoult it be r.Num...
        BufferFullNow = false;
        LengthBufferFree = 0;
        AverageLengthTimeBufferFree = 0;
        PercentTimeBufferFull = 0;

        AverageMessageDelay = 0;

        PercentBufferAvailable = 100.0;
        EgoNetPercentBufferAvailable = 100.0;

        EgoNetAverageLengthTimeBufferFree = 0;
        EgoNetPercentTimeBufferFull = 0;
        EgoNetAverageMessageDelay = 0;

    }

    @Override
    public MessageRouter replicate() {
        return new CafeRouter(this);
    }

    /**
     * Called whenever a connection goes up or comes down.
     */
    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);

        /*
         * The paper for this router describes Message summary vectors 
         * (from the original Epidemic paper), which
         * are exchanged between hosts when a connection is established. This
         * functionality is already handled by the simulator in the protocol
         * implemented in startTransfer() and receiveMessage().
         * 
         * Below we need to implement sending the corresponding message.
         */
        DTNHost thisHost = getHost();
        DTNHost peer = con.getOtherNode(thisHost);

        //do this when con is up and goes down (might have been up for awhile)
        if (recentEncounters.containsKey(peer)) {
            EncounterInfo info = recentEncounters.get(peer);
            info.updateEncounterTime(SimClock.getTime());
            updateFnDn();
        } else {
            // 
            //recentEncounters.put(peer, new EncounterInfo(SimClock.getTime()));
            // we initialise with optimum values (before we get real info from peer)
            recentEncounters.put(peer, new EncounterInfo(SimClock.getTime(), 1, this.getBufferSize(), 0, 1, this.getBufferSize(), 0, 100.0, 100.0));

        }

        if (!con.isUp()) {
            neighborEncounters.remove(peer);
            return;
        }

        /*
         * For this simulator, we just need a way to give the other node in this connection
         * access to the peers we recently encountered; so we duplicate the recentEncounters
         * Map and attach it to a message.
         */
        int msgSize = recentEncounters.size() * 64 + getMessageCollection().size() * 8;
        //
        msgSize += 64 * 6; // long.size() + double.size();

        Message newMsg = new Message(thisHost, peer, SUMMARY_XCHG_IDPREFIX + protocolMsgIdx++, msgSize, null);
        newMsg.addProperty(SUMMARY_XCHG_PROP, /*new HashMap<DTNHost, EncounterInfo>(*/ recentEncounters);

        //
        newMsg.addProperty(AVGTIMEBUFFERFREE_XCHG_PROP, AverageLengthTimeBufferFree);
        newMsg.addProperty(PRCTIMEBUFFERFULL_XCHG_PROP, PercentTimeBufferFull);
        newMsg.addProperty(AVGMSGDELAY_XCHG_PROP, AverageMessageDelay);

        newMsg.addProperty(EGOAVGTIMEBUFFERFREE_XCHG_PROP, EgoNetAverageLengthTimeBufferFree);
        newMsg.addProperty(EGOPRCTIMEBUFFERFULL_XCHG_PROP, EgoNetPercentTimeBufferFull);
        newMsg.addProperty(EGOAVGMSGDELAY_XCHG_PROP, EgoNetAverageMessageDelay);

        newMsg.addProperty(EBC_XCHG_PROP, new Double(this.EBC));
        newMsg.addProperty(Fn_XCHG_PROP, new Integer(this.Fn));
        newMsg.addProperty(Dn_XCHG_PROP, new Double(this.Dn));
        newMsg.addProperty(L_XCHG_PROP, new Double(L()));

        createNewMessage(newMsg);
    }

    @Override
    public boolean createNewMessage(Message m) {
        makeRoomForNewMessage(m.getSize());

        m.addProperty(MSG_COUNT_PROP, new Integer(initialNrofCopies));
        addToMessages(m, true);
        return true;
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message m = super.messageTransferred(id, from);

        /*
         * Here we update our last encounter times based on the information sent
         * from our peer. 
         */
        Map<DTNHost, EncounterInfo> peerEncounters = (Map<DTNHost, EncounterInfo>) m.getProperty(SUMMARY_XCHG_PROP);
        if (isDeliveredMessage(m) && peerEncounters != null) {
            double distTo = getHost().getLocation().distance(from.getLocation());
            double speed = from.getPath() == null ? 0 : from.getPath().getSpeed();

            if (speed == 0.0) {
                return m;
            }

            double timediff = distTo / speed;

            /*
             * We save the peer info for the utility based forwarding decisions, which are
             * implemented in update()
             */
            neighborEncounters.put(from, peerEncounters);

            for (Map.Entry<DTNHost, EncounterInfo> entry : peerEncounters.entrySet()) {
                DTNHost h = entry.getKey();
                if (h == getHost()) {
                    continue;
                }

                EncounterInfo peerEncounter = entry.getValue();
                EncounterInfo info = recentEncounters.get(h);

                /*
                 * We set our timestamp for some node, h, with whom our peer has come in contact
                 * if our peer has a newer timestamp beyond some threshold.
                 * 
                 * The paper describes timers that count up from the time of contact. We use
                 * fixed timestamps here to accomplish the same effect, but the computations
                 * here are consequently a little different from the paper. 
                 */
                if (!recentEncounters.containsKey(h)) {
                    info = new EncounterInfo(peerEncounter.getLastSeenTime() - timediff,
                            peerEncounter.getAvgBuffFree(), peerEncounter.getPctBuffFull(), peerEncounter.getAverageMsgDelay(),
                            peerEncounter.getEgoAvgBuffFree(), peerEncounter.getEgoPctBuffFull(), peerEncounter.getEgoAverageMsgDelay(),
                            peerEncounter.getPercentBufferAvailable(), peerEncounter.getEgoPercentBufferAvailable());
                    int _sim = similarity(recentEncounters.keySet(), neighborEncounters.get(from).keySet());
                    info.setSim(_sim);
                    info.setStats(((Double) m.getProperty(EBC_XCHG_PROP)).doubleValue(),
                            ((Integer) m.getProperty(Fn_XCHG_PROP)).intValue(),
                            ((Double) m.getProperty(Dn_XCHG_PROP)).doubleValue(),
                            ((Double) m.getProperty(L_XCHG_PROP)).doubleValue());
                    betweenness_update();
                    recentEncounters.put(h, info);
                    continue;
                }

                if (info.getLastSeenTime() + timediff < peerEncounter.getLastSeenTime()) {

                    //recentEncounters.get(h).updateEncounterTime(peerEncounter.getLastSeenTime() - timediff, peerEncounter.getAvgBuffFree(), peerEncounter.getPctBuffFull());
                    recentEncounters.get(h).updateEncounterTime(peerEncounter.getLastSeenTime() - timediff);

                    recentEncounters.get(h).updateAvgBuffFree(peerEncounter.getAvgBuffFree());
                    recentEncounters.get(h).updatePctBuffFull(peerEncounter.getPctBuffFull());
                    recentEncounters.get(h).updateAvgMsgDelay(peerEncounter.getAverageMsgDelay());

                    recentEncounters.get(h).updateEgoAvgBuffFree(peerEncounter.getEgoAvgBuffFree());
                    recentEncounters.get(h).updateEgoPctBuffFull(peerEncounter.getEgoPctBuffFull());
                    recentEncounters.get(h).updateEgoAvgMsgDelay(peerEncounter.getEgoAverageMsgDelay());
//EncounterInfo info = recentEncounters.get(from);

                    int _sim = similarity(recentEncounters.keySet(), neighborEncounters.get(from).keySet());
                    recentEncounters.get(h).setSim(_sim);
                    recentEncounters.get(h).setStats(((Double) m.getProperty(EBC_XCHG_PROP)).doubleValue(),
                            ((Integer) m.getProperty(Fn_XCHG_PROP)).intValue(),
                            ((Double) m.getProperty(Dn_XCHG_PROP)).doubleValue(),
                            ((Double) m.getProperty(L_XCHG_PROP)).doubleValue());
                    betweenness_update();
                }
            }
            return m;
        }

        //Normal message beyond here
        Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROP);

        nrofCopies = (int) Math.ceil(nrofCopies / 2.0);

        m.updateProperty(MSG_COUNT_PROP, nrofCopies);

        return m;
    }

    @Override
    protected void transferDone(Connection con) {
        Integer nrofCopies;
        String msgId = con.getMessage().getId();
        /* get this router's copy of the message */
        Message msg = getMessage(msgId);

        if (msg == null) { // message has been dropped from the buffer after..
            return; // ..start of transfer -> no need to reduce amount of copies
        }

        if (msg.getProperty(SUMMARY_XCHG_PROP) != null) {
            deleteMessage(msgId, false);
            return;
        }

        /* 
         * reduce the amount of copies left. If the number of copies was at 1 and
         * we apparently just transferred the msg (focus phase), then we should
         * delete it. 
         */
        nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROP);
        if (nrofCopies > 1) {
            nrofCopies /= 2;
        } else {
            deleteMessage(msgId, false);
        }

        msg.updateProperty(MSG_COUNT_PROP, nrofCopies);
    }

    @Override
    public void update() {
        super.update();
        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring 
        }

        // 
        // calculate percentate of time buffer full and average Length of buffer-free periods
        if (this.getFreeBufferSize() <= 0) {
            TimeBufferFull++;

            if (!BufferFullNow) {
                NumberOfTimesBufferFree++;
                BufferFullNow = true;
                LengthBufferFree = 0;
            }

        } else {
            LengthBufferFree++;
        }

        PercentTimeBufferFull = (double) TimeBufferFull / SimClock.getTime();
        AverageLengthTimeBufferFree = (AverageLengthTimeBufferFree * NumberOfTimesBufferFree + LengthBufferFree) / (NumberOfTimesBufferFree + 1);

        // EGO
        int cnt = 0;
        long egoAvgLenTimeBuffFree = 0;
        double egoPctTimeBuffFull = 0;
        int egoAvgMsgDelay = 0;

        for (Map.Entry<DTNHost, EncounterInfo> entry : recentEncounters.entrySet()) {

            EncounterInfo peerEncounter = entry.getValue();

            egoAvgLenTimeBuffFree += peerEncounter.getAvgBuffFree();
            egoPctTimeBuffFull += peerEncounter.getPctBuffFull();
            egoAvgMsgDelay += peerEncounter.getAverageMsgDelay();

            cnt++;
        }

        EgoNetAverageLengthTimeBufferFree /= cnt;
        EgoNetPercentTimeBufferFull /= cnt;
        EgoNetAverageMessageDelay /= cnt;

        /* try messages that could be delivered to final recipient */
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        List<Message> spraylist = new ArrayList<Message>();
        List<Tuple<Message, Connection>> focuslist = new LinkedList<Tuple<Message, Connection>>();

        for (Message m : getMessageCollection()) {
            if (m.getProperty(SUMMARY_XCHG_PROP) != null) {
                continue;
            }

            Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROP);
            assert nrofCopies != null : "Cafe message " + m + " didn't have "
                    + "nrof copies property!";
            if (nrofCopies > 1) {
                spraylist.add(m);
            } else {
                /*
                 * Here we implement the single copy utility-based forwarding scheme.
                 * The utility function is the last encounter time of the msg's 
                 * destination node. If our peer has a newer time (beyond the threshold),
                 * we forward the msg on to it. 
                 */
                DTNHost dest = m.getTo();
                Connection toSend = null;

                double maxPeerLastSeen = 0.0; //beginning of time (simulation time)

                //Get the timestamp of the last time this Host saw the destination
                double thisLastSeen = getLastEncounterTimeForHost(dest);

                // Cafe utility function - finds the best of the contacts
                //toSend = NextHopForDest();

                /* SnF utility function - who saw the connection last */
                //double TU_PercentTimeBufferAvailable = info.getPercentTimeBufferAvailable(); // in terms of time
                //double TU_EgoNetPercentTimeBufferAvailable = info.EgetgoNetPercentTimeBufferAvailable(); // in terms of time
                double TU_PercentBufferAvailable = PercentBufferAvailable; 		// in terms of space

                double TU_EgoNetPercentBufferAvailable = EgoNetPercentBufferAvailable;//EgoPercentBufferAvailable;

                ///////////////////////////////////////////////////////
                long TU_AverageLengthTimeBufferFree;
                double TU_PercentTimeBufferFull;

                int TU_AverageMessageDelay;

                double TU_Congestion;

/////////////////////////////////////////////////////
                long TU_EgoNetAverageLengthTimeBufferFree;
                double TU_EgoNetPercentTimeBufferFull;

                int TU_EgoNetAverageMessageDelay;

                double TU_EgoNetCongestion;

/////////////////////////////////////////////////////
                // implement percentage of congested nodes encountered
                double Best_TU = 0.0;
                for (Connection c : getConnections()) {
                    double TU_Social = GetSimBetTSValueNextHopForDest(m, c);// / 3.0;
                    DTNHost peer = c.getOtherNode(getHost());
                    Map<DTNHost, EncounterInfo> peerEncounters = neighborEncounters.get(peer);

                    EncounterInfo info = recentEncounters.get(peer);

                    // - total utilities
                    // OK
                    TU_AverageMessageDelay = 0;//1 - (AverageMessageDelay / (AverageMessageDelay + info.getAverageMsgDelay()));
                    TU_EgoNetAverageMessageDelay = 0;//1 - (EgoNetAverageMessageDelay / (EgoNetAverageMessageDelay + info.getEgoAverageMsgDelay()));

                    TU_PercentBufferAvailable = PercentBufferAvailable / (PercentBufferAvailable + info.getPercentBufferAvailable());
                    TU_EgoNetPercentBufferAvailable = EgoNetPercentBufferAvailable / (EgoNetPercentBufferAvailable + info.getEgoPercentBufferAvailable());

                    // OK
                    TU_Congestion = 1 - ((PercentTimeBufferFull / AverageLengthTimeBufferFree) / ((PercentTimeBufferFull / AverageLengthTimeBufferFree) + (info.getPctBuffFull() / info.getAvgBuffFree())));
                    TU_EgoNetCongestion = 1 - ((EgoNetPercentTimeBufferFull / EgoNetAverageLengthTimeBufferFree) / ((EgoNetPercentTimeBufferFull / EgoNetAverageLengthTimeBufferFree) + (info.getEgoPctBuffFull() / info.getEgoAvgBuffFree())));

                    ////////////////////////////////////////
					/*
                     double TU1 = (TU_Social + 
                     TU_AverageMessageDelay + TU_PercentBufferAvailable + TU_Congestion + 
                     TU_EgoNetAverageMessageDelay + TU_EgoNetPercentBufferAvailable + TU_EgoNetCongestion) * 
                     (this.getFreeBufferSize() / this.getBufferSize()); 
					
                     double TU2 = (TU_Social + 
                     TU_AverageMessageDelay+TU_PercentBufferAvailable + TU_Congestion ) * 
                     (this.getFreeBufferSize() / this.getBufferSize()); 
					
                     double TU3 = (TU_Social + 
                     TU_AverageMessageDelay + TU_PercentBufferAvailable + 
                     TU_EgoNetPercentBufferAvailable) * 
                     (this.getFreeBufferSize() / this.getBufferSize()); 
					
                     double TU4 = (TU_Social + 
                     TU_AverageMessageDelay + TU_PercentBufferAvailable +
                     TU_EgoNetCongestion) * 					(this.getFreeBufferSize() / this.getBufferSize()); 
					
                     double TU5 = (TU_Social + 
                     TU_AverageMessageDelay + TU_PercentBufferAvailable + 
                     TU_EgoNetAverageMessageDelay) * 
                     (this.getFreeBufferSize() / this.getBufferSize()); 
					
                     double TU6 = (TU_Social + 
                     TU_AverageMessageDelay + TU_PercentBufferAvailable 	) * 
                     (this.getFreeBufferSize() / this.getBufferSize()); 
					
                     double TU7 = (TU_Social + 
                     TU_AverageMessageDelay + TU_PercentBufferAvailable + TU_PercentTimeBufferAvailable) * 
                     (this.getFreeBufferSize() / this.getBufferSize()); 
					
                     double TU8 = (TU_Social + 
                     TU_AverageMessageDelay + TU_PercentBufferAvailable + TU_Congestion + TU_PercentTimeBufferAvailable +
                     TU_EgoNetAverageMessageDelay + TU_EgoNetPercentBufferAvailable + TU_EgoNetCongestion) * (this.getFreeBufferSize() / this.getBufferSize()); 
			
                     double TU9 = (TU_Social + 
                     TU_AverageMessageDelay + TU_PercentBufferAvailable + 
                     TU_EgoNetPERCENTAGE_OF_FULL_NODES) * 
                     (this.getFreeBufferSize() / this.getBufferSize()); 
					
                     double TU10 = (TU_Social + 
                     TU_AverageMessageDelay + TU_PercentBufferAvailable + TU_Congestion + TU_AVERGAE_BUFFER_UTILITY
                     +TU-EgoNetPERCENTAGE_OF_FULL_NODES+TU_EgoNetAverageMessageDelay + TU_EgoNetPercentBufferAvailable + TU_EgoNetCongestion) * 
                     (this.getFreeBufferSize() / this.getBufferSize());
                     */
                    /////////////////////////////////////////////////////
                    double TU_NEW1 = (TU_Social
                            * (this.getFreeBufferSize() / this.getBufferSize()));

                    double TU_NEW2 = ((TU_Social + TU_AverageMessageDelay + TU_PercentBufferAvailable + TU_Congestion)
                            * (this.getFreeBufferSize() / this.getBufferSize()));

                    double TU_NEW3 = ((TU_Social + TU_AverageMessageDelay + TU_PercentBufferAvailable + TU_Congestion + TU_EgoNetPercentBufferAvailable)
                            * (this.getFreeBufferSize() / this.getBufferSize()));

                    double TU_NEW4 = ((TU_Social + TU_EgoNetPercentBufferAvailable)
                            * (this.getFreeBufferSize() / this.getBufferSize()));

                    /////////////////////////////////////////////////////
                    double tmp = TU_Social
                            + TU_AverageMessageDelay + TU_PercentBufferAvailable + TU_Congestion
                            + TU_EgoNetAverageMessageDelay + TU_EgoNetPercentBufferAvailable + TU_EgoNetCongestion;
                    double TTU = tmp;
                    System.out.printf("WTF\n");
System.out.printf("%f- %d- %f- %f- %d- %f- %f-\n",TU_Social, TU_AverageMessageDelay, TU_PercentBufferAvailable, TU_Congestion
                            ,TU_EgoNetAverageMessageDelay,TU_EgoNetPercentBufferAvailable,TU_EgoNetCongestion);
                    /////////////////////////////////////////////////////////////
                    double peerLastSeen = 0.0;

                    //if(peerEncounters != null && peerEncounters.containsKey(dest))
                    //peerLastSeen = neighborEncounters.get(peer).get(dest).getLastSeenTime();
                    if (TTU > Best_TU) //if(peerLastSeen > maxPeerLastSeen)
                    {
                        Best_TU = TTU;
                        toSend = c;
                        //maxPeerLastSeen = peerLastSeen;
                    }

                }
                /*	*/

                /* SNF  - is the best connection "toSend" better than us */
                //if (toSend != null && maxPeerLastSeen > thisLastSeen + transitivityTimerThreshold)
                if (toSend != null) {
                    focuslist.add(new Tuple<Message, Connection>(m, toSend));
                }

                // CAFE - compare to us
                //DTNHost HostToSend = toSend.getOtherNode(getHost());
                //if(recentEncounters.containsKey(HostToSend))
                //{ 
                //EncounterInfo HostToSendEncInfo = recentEncounters.get(toSend.getOtherNode(getHost()));
                // here we check how good the best one is compared to us
                // buffer
					/*
                 boolean  bufferBetter = false;
                 if (HostToSendEncInfo.getPctBuffFull() == 0)
                 bufferBetter = true;
                 else if (this.PercentTimeBufferFull == 0 )
                 bufferBetter = false;
                 else if ( 	this.AverageLengthTimeBufferFree / this.PercentTimeBufferFull < HostToSendEncInfo.getAvgBuffFree() / HostToSendEncInfo.getPctBuffFull()	)
                 bufferBetter = true;
					
                 // message delay
                 boolean messageDelayBetter = false;
                 if (this.AverageMessageDelay > HostToSendEncInfo.getAverageMsgDelay())
                 messageDelayBetter = true;
                 */
                //}
                //}
            }

            //arbitrarily favor spraying
            if (tryMessagesToConnections(spraylist, getConnections()) == null) {
                if (tryMessagesForConnected(focuslist) != null) {
                }
            }
        }
    }

    protected double getLastEncounterTimeForHost(DTNHost host) {
        if (recentEncounters.containsKey(host)) {
            return recentEncounters.get(host).getLastSeenTime();
        } else {
            return 0.0;
        }
    }

    protected void getEgoNet() {
    }

    //////////////////////////////////////////////
    /**
     * - find best next hop so that we can forward packet to them
     */
    protected double GetSimBetTSValueNextHopForDest(Message m, Connection c) {
        DTNHost dest = m.getTo();
        EncounterInfo d = recentEncounters.get(dest);
        int Sim;
        double Bet, TS;
        if (d != null) {
            // our similarity to d
            Sim = d.sim();

            double FI, ICI, RecI;
            // Our Tie Strength
            // FI = f(m) / F(n) - f(m)
            if ((this.Fn - d.f()) != 0) {
                FI = (double) (d.f() / (double) (this.Fn - d.f()));
            } else {
                FI = d.f();
            }
            // ICI = d(m) / D(n) - d(m)
            if ((this.Dn - d.d()) != 0) {
                ICI = (double) (d.d() / (double) (this.Dn - d.d()));
            } else {
                ICI = (double) d.d();
            }
            // RecI = rec(m) / L(n) - rec(m)
            double L = L();
            if ((L - d.rec()) != 0) {
                RecI = (double) d.rec() / (double) (L - d.rec());
            } else {
                RecI = (double) d.rec();
            }
            TS = FI + ICI + RecI;
        } else {

            Sim = 0;
            TS = 0.0;
        }

        // Our betweenness
        Bet = this.EBC;

        DTNHost e = c.getOtherNode(getHost());

        EncounterInfo n = recentEncounters.get(e);

        EncounterInfo nd = null;
        if (neighborEncounters.get(e) != null) {
            nd = neighborEncounters.get(e).get(dest);
        }
        //System.out.println("S: " + d.sim() + " D: " + nd.sim());
        double RecI_nd = 0.0;
        double ICI_nd = 0.0;
        double FI_nd = 0.0;

        if (nd != null) {
            if ((nd.Fn() - nd.f()) != 0) {
                FI_nd = (double) nd.f() / (double) (nd.Fn() - nd.f());
            } else {
                FI_nd = (double) nd.f();
            }

            if ((nd.Dn() - nd.d()) != 0) {
                ICI_nd = (double) nd.d() / (double) (nd.Dn() - nd.d());
            } else {
                ICI_nd = (double) nd.d();
            }

            if ((nd.L() - nd.rec()) != 0) {
                RecI_nd = (double) nd.rec() / (double) (nd.L() - nd.rec());
            } else {
                RecI_nd = (double) nd.rec();
            }
        }
        double TS_nd = FI_nd + ICI_nd + RecI_nd;

        // utility values comparing this node to m
        double SimUtil = 0.0;
        double BetUtil = 0.0;
        double TSUtil = 0.0;

        if (nd != null && (nd.sim() + Sim) != 0) {
            SimUtil = (double) nd.sim() / (double) (nd.sim() + Sim);
            //System.out.println("SimIs: " + SimUtil + " Hello: " + nd.sim() + " BLoow: " + Sim);
        }
        //System.out.println("Bet:" + (n.ebc() + Bet));
        if (n != null && (n.ebc() + Bet) != 0) {
            BetUtil = (double) n.ebc() / (double) (n.ebc() + Bet);

        }

        if ((TS_nd + TS) != 0) {
            TSUtil = (double) TS_nd / (double) (TS_nd + TS);
        }

        double alpha = 0.3333;
        double beta = 0.3333;
        double delta = 0.3333;
        double Util_nd = alpha * SimUtil + beta * BetUtil + delta * TSUtil;

        // see if this node is a better next hop
        return Util_nd;
    }

    public double L() {
        return SimClock.getTime() - TOC;
    }

    public int similarity(Set<DTNHost> _A, Set<DTNHost> _B) {
        Object[] A = _A.toArray();
        Object[] B = _B.toArray();
        int sim = 0;
        for (Object _a : A) {
            DTNHost a = (DTNHost) _a;
            for (Object _b : B) {
                DTNHost b = (DTNHost) _b;
                if (a == b) {
                    sim++;
                    break;
                }
            }
        }
        return sim;
    }

    public void betweenness_update() {
        int Ni = recentEncounters.size();
        // Adjacency Matrix
        int A[][] = new int[Ni][Ni];

        //
        Object[] fstHopIDs = recentEncounters.keySet().toArray();
        // for each contact in our contact set
        for (int i = 0; i < Ni; i++) {
            DTNHost c1 = (DTNHost) fstHopIDs[i];
            // see if they require us to connect them to our other contacts
            for (int j = 0; j < Ni; j++) {
                // dont try to connect them with themselves
                if (i != j) {
                    DTNHost c2 = (DTNHost) fstHopIDs[j];
                    boolean adj = false;
                    if (neighborEncounters.get(c1) != null) {
                        for (Object _c1c : neighborEncounters.get(c1).keySet().toArray()) {
                            DTNHost c1c = (DTNHost) _c1c;
                            if (c1c == c2) {
                                adj = true;
                                break;
                            }
                        }
                    }
                    if (adj) {
                        A[i][j] = 1;
                    } else {
                        A[i][j] = 0;
                    }
                } else {
                    A[i][j] = 0;
                }
            }
        }

        // A^2
        double A2[][] = new double[Ni][Ni];;
        for (int i = 0; i < Ni; i++) {
            for (int j = 0; j < Ni; j++) {
                A2[i][j] = 0;
                for (int k = 0; k < Ni; k++) {
                    A2[i][j] += A[i][k] * A[k][j];
                }
            }
        }

        // A^2[1-A]ij
        double A3[][] = new double[Ni][Ni];;
        for (int i = 0; i < Ni; i++) {
            for (int j = 0; j < Ni; j++) {
                if (A2[i][j] != 0) {
                    A3[i][j] = 1.0 / (double) A2[i][j];
                }
            }
        }

        // Sum of values for dependant contacts
        double b = 0.0;
        for (int i = 0; i < Ni; i++) {
            for (int j = i + 1; j < Ni; j++) {

                if (A[i][j] == 0) {

                    b += A3[i][j];

                }
            }
        }

        this.EBC = b;

    }

    protected void updateFnDn() {
        Object[] keys = recentEncounters.keySet().toArray();
        int _Fn = 0;
        int _Dn = 0;
        for (Object _k : keys) {
            DTNHost k = (DTNHost) _k;
            EncounterInfo h = recentEncounters.get(k);
            _Dn += h.d();
            _Fn += h.f();
        }
        Dn = _Dn;
        Fn = _Fn;

    }

    // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- //
    ////
    // Frequency
    // f(m) = frequency of interactions with m
    // f(m, D) = frequency of interactions between m and D
    ////
    protected double f(DTNHost m) {
        if (recentEncounters.containsKey(m)) {
            return recentEncounters.get(m).rec();
        } else {
            return 0.0;
        }
    }

    protected double f(DTNHost m, DTNHost D) {
        if (neighborEncounters.get(m).containsKey(D)) {
            return neighborEncounters.get(m).get(D).rec();
        } else {
            return 0.0;
        }
    }

    ////
    // Closeness
    // d(m) = total contact duration with node m
    ////
    protected double d(DTNHost m) {
        if (recentEncounters.containsKey(m)) {
            return recentEncounters.get(m).rec();
        } else {
            return 0.0;
        }
    }

    ////
    // Recency
    // rec(m) = time since m was last seen
    ////
    protected double rec(DTNHost m) {
        if (recentEncounters.containsKey(m)) {
            return recentEncounters.get(m).rec();
        } else {
            return 0.0;
        }
    }

    ////
    // Similarity
    // sim(m) = number of contacts in common with m
    ////
    protected double sim(DTNHost m) {
        if (recentEncounters.containsKey(m)) {
            return recentEncounters.get(m).rec();
        } else {
            return 0.0;
        }
    }

    ////
    // Ego-Betweenness Centrality
    ////
    protected double ebc(DTNHost m) {
        if (recentEncounters.containsKey(m)) {
            return recentEncounters.get(m).rec();
        } else {
            return 0.0;
        }
    }

    //////////////////////////////////////////////
    /**
     * Stores all necessary info about encounters made by this host to some
     * other host.
     *
     */
    protected class EncounterInfo {

        protected double seenAtTime;
        //
        protected long AverageLengthTimeBufferFree;
        protected double PercentTimeBufferFull;
        protected int AverageMsgDelay;
        protected long EgoAverageLengthTimeBufferFree;
        protected double EgoPercentTimeBufferFull;
        protected int EgoAverageMsgDelay;
        protected double pctBufferAvailable;
        protected double pctEgoBufferAvailable;
        protected double f;
        protected double d;
        protected double rec;
        protected int sim;
        protected double ebc;
        protected int Fn;
        protected double Dn;
        protected double L;

        public EncounterInfo(double atTime, long avgLenTimeBuffFree, double pctTimeFuffFull, int avgMsgDelay, long EgoavgLenTimeBuffFree, double EgopctTimeFuffFull, int EgoavgMsgDelay, double pctBuffAvail, double pctEgoBuffAvail) {
            this.seenAtTime = atTime;

            this.AverageLengthTimeBufferFree = avgLenTimeBuffFree;
            this.PercentTimeBufferFull = pctTimeFuffFull;
            this.AverageMsgDelay = avgMsgDelay;

            this.EgoAverageLengthTimeBufferFree = EgoavgLenTimeBuffFree;
            this.EgoPercentTimeBufferFull = EgopctTimeFuffFull;
            this.EgoAverageMsgDelay = EgoavgMsgDelay;

            this.pctBufferAvailable = pctBuffAvail;
            this.pctEgoBufferAvailable = pctEgoBuffAvail;

            this.f = 0;
            this.d = 0;
            this.rec = atTime;
        }

        public void updateEncounterTime(double atTime) {
            this.seenAtTime = atTime;
        }

        public double getLastSeenTime() {
            return seenAtTime;
        }

        public void updateLastSeenTime(double atTime) {
            this.seenAtTime = atTime;
        }

        // 
        public long getAvgBuffFree() {
            return AverageLengthTimeBufferFree;
        }

        public void updateAvgBuffFree(long avgLenTimeBuffFree) {
            this.AverageLengthTimeBufferFree = avgLenTimeBuffFree;
        }
        /////////////////////////////

        public double getPctBuffFull() {
            return PercentTimeBufferFull;
        }

        public void updatePctBuffFull(double pctTimeFuffFull) {
            this.PercentTimeBufferFull = pctTimeFuffFull;
        }
        //////////////////

        public int getAverageMsgDelay() {
            return this.AverageMsgDelay;
        }

        public void updateAvgMsgDelay(int avgMsgDelay) {
            this.AverageMsgDelay = avgMsgDelay;
        }
        /////////
        ////////

        public long getEgoAvgBuffFree() {
            return EgoAverageLengthTimeBufferFree;
        }

        public void updateEgoAvgBuffFree(long EgoavgLenTimeBuffFree) {
            this.EgoAverageLengthTimeBufferFree = EgoavgLenTimeBuffFree;
        }

        /////////////////////////////
        public double getEgoPctBuffFull() {
            return EgoPercentTimeBufferFull;
        }

        public void updateEgoPctBuffFull(double EgopctTimeFuffFull) {
            this.EgoPercentTimeBufferFull = EgopctTimeFuffFull;
        }
        //////////////////

        public int getEgoAverageMsgDelay() {
            return this.EgoAverageMsgDelay;
        }

        public void updateEgoAvgMsgDelay(int EgoavgMsgDelay) {
            this.EgoAverageMsgDelay = EgoavgMsgDelay;
        }

        //////////////////
        public double getPercentBufferAvailable() {
            return this.pctBufferAvailable;
        }

        public void updatePercentBufferAvailable(double pctBuffAvail) {
            this.pctBufferAvailable = pctBuffAvail;
        }

        public double getEgoPercentBufferAvailable() {
            return this.pctEgoBufferAvailable;
        }

        public void updateEgoPercentBufferAvailable(double pctEgoBuffAvail) {
            this.pctEgoBufferAvailable = pctEgoBuffAvail;
        }

        public void updateEncounter(double _time, boolean _isUp) {
            if (!_isUp) {
                this.f++;
                this.d += _time - this.rec;
            }
            this.rec = _time;

        }

        public void setSim(int _sim) {
            this.sim = _sim;
        }

        public void setStats(double _ebc, int _Fn, double _Dn, double _L) {
            this.ebc = _ebc;
            this.Fn = _Fn;
            this.Dn = _Dn;
            this.L = _L;
        }

        public double f() {
            return f;
        }

        public double d() {
            return d;
        }

        public double rec() {
            return rec;
        }

        public int sim() {
            return sim;
        }

        public double ebc() {
            return ebc;
        }

        public double Fn() {
            return Fn;
        }

        public double Dn() {
            return Dn;
        }

        public double L() {
            return L;
        }
    }
}
