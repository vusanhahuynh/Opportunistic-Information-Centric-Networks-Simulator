package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import routing.maxprop.MaxPropDijkstra;
import routing.maxprop.MeetingProbabilitySet;
import util.Tuple;
import core.Connection;
import core.Content;
import core.ContentType;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

public class IEEEAccess18RouterLRU extends ActiveRouter {

    /**
     * SprayAndFocus router's settings name space ({@value})
     */
    public static final String PROTOCOL_NS = "Social";
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
     * Message property key for summary vector messages exchanged between direct
     * peers
     */
    public static final String SUMMARY_XCHG_PROP = "HelloMessage.protoXchg";
    public static final String EBC_XCHG_PROP = "HelloMessage.ebcXchg";
    public static final String Fn_XCHG_PROP = "HelloMessage.FnXchg";
    public static final String Dn_XCHG_PROP = "HelloMessage.DnXchg";
    public static final String L_XCHG_PROP = "HelloMessage.LXchg";

    protected static final String SUMMARY_XCHG_IDPREFIX = "summary";
    protected static int protocolMsgIdx = 0;

    public static final String PROB_SET_MAX_SIZE_S = "probSetMaxSize";
    /**
     * Default value for the meeting probability set maximum size ({@value}).
     */
    public static final int DEFAULT_PROB_SET_MAX_SIZE = 50;
    private static int probSetMaxSize;

    /**
     * probabilities of meeting hosts
     */
    private MeetingProbabilitySet probs;
    /**
     * meeting probabilities of all hosts from this host's point of view mapped
     * using host's network address
     */
    private Map<Integer, MeetingProbabilitySet> allProbs;
    /**
     * the cost-to-node calculator
     */
    private MaxPropDijkstra dijkstra;
    /**
     * IDs of the messages that are known to have reached the final dst
     */
    /**
     * mapping of the current costs for all messages. This should be set to null
     * always when the costs should be updated (a host is met or a new message
     * is received)
     */
    private Map<Integer, Double> costsForMessages;
    /**
     * From host of the last cost calculation
     */
    private DTNHost lastCostFrom;

    /**
     * Map of which messages have been sent to which hosts from this host
     */
    private Map<DTNHost, Set<String>> sentMessages;

    /**
     * Over how many samples the "average number of bytes transferred per
     * transfer opportunity" is taken
     */
    public static int BYTES_TRANSFERRED_AVG_SAMPLES = 10;
    private int[] avgSamples;
    private int nextSampleIndex = 0;
    /**
     * current value for the "avg number of bytes transferred per transfer
     * opportunity"
     */
    private int avgTransferredBytes = 0;

    /**
     * The alpha parameter string
     */
    public static final String ALPHA_S = "alpha";

    /**
     * The alpha variable, default = 1;
     */
    private double alpha;

    /**
     * The default value for alpha
     */
    public static final double DEFAULT_ALPHA = 1.0;

    private Set<String> ackedMessageIds;
    /**
     * Stores information about nodes with which this host has come in contact
     */
    protected Map<DTNHost, EncounterInfo> fstHopEncounters;
    protected Map<DTNHost, Map<DTNHost, EncounterInfo>> secHopEncounters;

    // Ego Betweenness Centrality
    protected double EBC;
    // F(n) = total contact frequency
    protected int Fn;
    // D(n) = total contact duration
    protected double Dn;
    // Time of Creation
    protected double TOC;

    //////////////////////////////////////// Congestion Avoidance /////////////////////////////////////////
    private int numberOfTimeCongested = 0;
    private ArrayList<Double> congestionDuration = new ArrayList<>();
    private ArrayList<Double> congestionGap = new ArrayList<>();
    private double congestionRecency = Double.MAX_VALUE;
    private double congestionStartTime = -1.0;
    private double congestionEndTime = -1.0;
    private boolean isCongesting = false;
    private double startTime;
    private double freeTime = -1.0;

    protected Map<DTNHost, ArrayList<Double>> reputationHistory;

    protected Map<DTNHost, ArrayList<Double>> encountersHistory;
    //////////////////////////////////////// Congestion Avoidance /////////////////////////////////////////

    public IEEEAccess18RouterLRU(Settings s) {
        super(s);
        //Settings setts = new Settings(PROTOCOL_NS);
        fstHopEncounters = new HashMap<DTNHost, EncounterInfo>();
        secHopEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();
        encountersHistory = new HashMap<DTNHost, ArrayList<Double>>();

        EBC = 0.0;
        Fn = 0;
        Dn = 0.0;
        TOC = SimClock.getTime();
        reputationHistory = new HashMap<DTNHost, ArrayList<Double>>();

        Settings socialSettings = new Settings(PROTOCOL_NS);
        if (socialSettings.contains(ALPHA_S)) {
            alpha = socialSettings.getDouble(ALPHA_S);
        } else {
            alpha = DEFAULT_ALPHA;
        }

        Settings mpSettings = new Settings(PROTOCOL_NS);
        if (mpSettings.contains(PROB_SET_MAX_SIZE_S)) {
            probSetMaxSize = mpSettings.getInt(PROB_SET_MAX_SIZE_S);
        } else {
            probSetMaxSize = DEFAULT_PROB_SET_MAX_SIZE;
        }
        startTime = SimClock.getTime();
    }

    /**
     * Copy Constructor.
     *
     * @param r The router from which settings should be copied
     */
    public IEEEAccess18RouterLRU(IEEEAccess18RouterLRU r) {
        super(r);
        fstHopEncounters = new HashMap<DTNHost, EncounterInfo>();
        secHopEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();
        encountersHistory = new HashMap<DTNHost, ArrayList<Double>>();

        EBC = 0.0;
        Fn = 0;
        Dn = 0.0;
        TOC = SimClock.getTime();

        this.alpha = r.alpha;
        this.probs = new MeetingProbabilitySet(probSetMaxSize, this.alpha);
        this.allProbs = new HashMap<Integer, MeetingProbabilitySet>();
        this.dijkstra = new MaxPropDijkstra(this.allProbs);
        this.ackedMessageIds = new HashSet<String>();
        this.avgSamples = new int[BYTES_TRANSFERRED_AVG_SAMPLES];
        this.sentMessages = new HashMap<DTNHost, Set<String>>();
        startTime = SimClock.getTime();
        reputationHistory = new HashMap<DTNHost, ArrayList<Double>>();
    }

    @Override
    public MessageRouter replicate() {
        return new IEEEAccess18RouterLRU(this);
    }

    public void print(String s) {
        System.out.println(s);
    }

    /**
     * Called whenever a connection goes up or comes down.
     */
    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);

        DTNHost thisHost = getHost();
        DTNHost peer = con.getOtherNode(thisHost);

        // Do this when con is up and goes down (might have been up for awhile)
        if (fstHopEncounters.containsKey(peer)) {
            EncounterInfo info = fstHopEncounters.get(peer);
            info.updateEncounter(SimClock.getTime(), con.isUp());
            updateFnDn();
        } else {
            fstHopEncounters.put(peer, new EncounterInfo(SimClock.getTime()));
        }

        if (con.isUp()) {
            this.costsForMessages = null;
            // TODO: Update msgSize to reflect SimBetTS contact
            int msgSize = fstHopEncounters.size() * 64 + getMessageCollection().size() * 8;

            Message newMsg = new Message(thisHost, peer, SUMMARY_XCHG_IDPREFIX + protocolMsgIdx++, msgSize, null);
            newMsg.addProperty(SUMMARY_XCHG_PROP, fstHopEncounters);
            newMsg.addProperty(EBC_XCHG_PROP, new Double(this.EBC));
            newMsg.addProperty(Fn_XCHG_PROP, new Integer(this.Fn));
            newMsg.addProperty(Dn_XCHG_PROP, new Double(this.Dn));
            newMsg.addProperty(L_XCHG_PROP, new Double(L()));

            createNewMessage(newMsg);

            if (encountersHistory.containsKey(peer)) {
                ArrayList<Double> tmp = encountersHistory.get(peer);
                tmp.add(SimClock.getTime());
            } else {
                ArrayList<Double> tmp = new ArrayList<>();
                tmp.add(SimClock.getTime());
                encountersHistory.put(peer, tmp);
            }
            if (con.isInitiator(getHost())) {
                /* initiator performs all the actions on behalf of the
                 * other node too (so that the meeting probs are updated
                 * for both before exchanging them) */
                DTNHost otherHost = con.getOtherNode(getHost());
                IEEEAccess18RouterLRU otherRouter = (IEEEAccess18RouterLRU) otherHost.getRouter();
                /* update both meeting probabilities */
                probs.updateMeetingProbFor(otherHost.getAddress());
                otherRouter.probs.updateMeetingProbFor(getHost().getAddress());

                /* exchange ACKed message data */
                this.ackedMessageIds.addAll(otherRouter.ackedMessageIds);
                otherRouter.ackedMessageIds.addAll(this.ackedMessageIds);
                deleteAckedMessages();
                otherRouter.deleteAckedMessages();

                /* exchange the transitive probabilities */
                this.updateTransitiveProbs(otherRouter.allProbs);
                otherRouter.updateTransitiveProbs(this.allProbs);
                this.allProbs.put(otherHost.getAddress(),
                        otherRouter.probs.replicate());
                otherRouter.allProbs.put(getHost().getAddress(),
                        this.probs.replicate());
            }
        } else {
            /* connection went down, update transferred bytes average */
            updateTransferredBytesAvg(con.getTotalBytesTransferred());
        }
    }

    // Important to remove messages that have been delivered already => not waste resource.
    private void deleteAckedMessages() {
        for (String id : this.ackedMessageIds) {
            if (this.hasMessage(id) && !isSending(id)) {
                this.deleteMessage(id, false);
            }
        }
    }

    private void updateTransitiveProbs(Map<Integer, MeetingProbabilitySet> p) {
        for (Map.Entry<Integer, MeetingProbabilitySet> e : p.entrySet()) {
            MeetingProbabilitySet myMps = this.allProbs.get(e.getKey());
            if (myMps == null
                    || e.getValue().getLastUpdateTime() > myMps.getLastUpdateTime()) {
                this.allProbs.put(e.getKey(), e.getValue().replicate());
            }
        }
    }

    private void updateTransferredBytesAvg(int newValue) {
        int realCount = 0;
        int sum = 0;

        this.avgSamples[this.nextSampleIndex++] = newValue;
        if (this.nextSampleIndex >= BYTES_TRANSFERRED_AVG_SAMPLES) {
            this.nextSampleIndex = 0;
        }

        for (int i = 0; i < BYTES_TRANSFERRED_AVG_SAMPLES; i++) {
            if (this.avgSamples[i] > 0) { // only values above zero count
                realCount++;
                sum += this.avgSamples[i];
            }
        }

        if (realCount > 0) {
            this.avgTransferredBytes = sum / realCount;
        } else { // no samples or all samples are zero
            this.avgTransferredBytes = 0;
        }
    }

    @Override
    public boolean createNewMessage(Message m) {
        makeRoomForNewMessage(m.getSize());
        addToMessages(m, true);
        return true;
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        this.costsForMessages = null;
        Message m = super.messageTransferred(id, from);

        /*
         * Here we update our last encounter times based on the information sent
         * from our peer. 
         */
        Map<DTNHost, EncounterInfo> peerEncounters = (Map<DTNHost, EncounterInfo>) m.getProperty(SUMMARY_XCHG_PROP);
        if (isDeliveredMessage(m)) {
            if (peerEncounters != null) {

                /*
                 * We save the peer info for the utility based forwarding decisions, which are
                 * implemented in update()
                 */
                secHopEncounters.put(from, peerEncounters);
                EncounterInfo info = fstHopEncounters.get(from);
                int _sim = similarity(fstHopEncounters.keySet(), secHopEncounters.get(from).keySet());
                info.setSim(_sim);

                info.setStats(((Double) m.getProperty(EBC_XCHG_PROP)).doubleValue(),
                        ((Integer) m.getProperty(Fn_XCHG_PROP)).intValue(),
                        ((Double) m.getProperty(Dn_XCHG_PROP)).doubleValue(),
                        ((Double) m.getProperty(L_XCHG_PROP)).doubleValue());
                betweenness_update();

                if (this.hasMessage(m.getId()) && !isSending(m.getId())) {
                    this.deleteMessage(m.getId(), false);
                }
                return null;
            }
            this.ackedMessageIds.add(id);
        }
        //Normal message beyond here
        return m;
    }

    @Override
    protected void transferDone(Connection con) {
        Message m = con.getMessage();
        if (m == null) { // message has been dropped from the buffer after..
            return; // ..start of transfer -> no need to reduce amount of copies
        }
        if (m.getProperty(SUMMARY_XCHG_PROP) != null) {
            deleteMessage(m.getId(), false);
            return;
        }

        String id = m.getId();
        DTNHost recipient = con.getOtherNode(getHost());
        Set<String> sentMsgIds = this.sentMessages.get(recipient);

        /* was the message delivered to the final recipient? */
        if ((m.getTo() != null && m.getTo() == recipient)) { //|| m.getType() == 0 || m.getType() == 1
            this.ackedMessageIds.add(m.getId()); // yes, add to ACKed messages
            this.deleteMessage(m.getId(), false); // delete from buffer

            if (reputationHistory.containsKey(recipient)) {
                ArrayList<Double> tmp = reputationHistory.get(recipient);
                tmp.add(SimClock.getTime());
            } else {
                ArrayList<Double> tmp = new ArrayList<>();
                tmp.add(SimClock.getTime());
                reputationHistory.put(recipient, tmp);
            }
        }

        /* update the map of where each message is already sent */
        if (sentMsgIds == null) {
            sentMsgIds = new HashSet<String>();
            this.sentMessages.put(recipient, sentMsgIds);
        }
        sentMsgIds.add(id);
    }

    @Override
    protected Content getNextContentToRemove(Content newContent, boolean excludeMsgBeingSent) {
        Collection<Content> contentStore = this.getContentStore();

        Content oldest = null;
        for (Content c : contentStore) {

            if (c.getIsPublishedContent()) {
                continue; // skip the content that owned/published by the router
            }
            if (oldest == null) {
                oldest = c;
            } else if (oldest.getTimeOfReceived() > c.getTimeOfReceived()) {
            // else if (oldest.getContentId() < c.getContentId()) {
                oldest = c;
            }
        }
        return oldest;
    }


    /*@Override
    protected Message getNextMessageToRemove(boolean excludeMsgBeingSent) { // LRU
        Collection<Message> messages = this.getMessageCollection();
        Message oldest = null;
        for (Message m : messages) {
            if (m.getProperty(SUMMARY_XCHG_PROP) != null) {
                continue;
            }
            if (excludeMsgBeingSent && isSending(m.getId())) {
                continue; // skip the message(s) that router is sending
            }
            //if (m.getContent() != null && m.getContent().getContentType() == ContentType.Interest) {
            //    continue;
            //}

            if (oldest == null) {
                oldest = m;
            } else if (oldest.getReceiveTime() > m.getReceiveTime()) {
                oldest = m;
            }
        }
        return oldest;
    }*/
    
    @Override
    protected Message getNextMessageToRemove(boolean excludeMsgBeingSent) {
        Collection<Message> messages = this.getMessageCollection();
        List<Message> validMessages = new ArrayList<Message>();

        for (Message m : messages) {
            if (m.getProperty(SUMMARY_XCHG_PROP) != null) {
                continue;
            }
            if (excludeMsgBeingSent && isSending(m.getId())) {
                continue; // skip the message(s) that router is sending
            }
            validMessages.add(m);
        }
        return validMessages.get(validMessages.size() - 1); // return last message
    }

    @Override
    public void update() {

        super.update();

        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring 
        }

        /* try messages that could be delivered to final recipient */
        if (exchangeDeliverableMessages() != null) {
            return;
        }
        tryOtherMessages();
    }

    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages
                = new ArrayList<Tuple<Message, Connection>>();

        Collection<Message> msgCollection = getMessageCollection();

        /* for all connected hosts that are not transferring at the moment,
         * collect all the messages that could be sent */
        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());
            IEEEAccess18RouterLRU othRouter = (IEEEAccess18RouterLRU) other.getRouter();
            Set<String> sentMsgIds = this.sentMessages.get(other);

            if (othRouter.isTransferring()) {
                continue; // skip hosts that are transferring
            }

            for (Message m : msgCollection) {
                /* skip messages that the other host has or that have
                 * passed the other host */
                if (othRouter.hasMessage(m.getId())
                        || m.getHops().contains(other)) {
                    continue;
                }
                /* skip message if this host has already sent it to the other
                 host (regardless of if the other host still has it) */
                if (sentMsgIds != null && sentMsgIds.contains(m.getId())) {
                    continue;
                }
                if (m.getProperty(SUMMARY_XCHG_PROP) != null) {
                    continue;
                }

                /* message was a good candidate for sending */
                messages.add(new Tuple<Message, Connection>(m, con));
            }
        }

        if (messages.size() == 0) {
            return null;
        }
        /* sort the message-connection tuples according to the criteria
         * defined in MaxPropTupleComparator */
        messages = socialTheorySort(messages);

        return tryMessagesForConnected(messages);
    }

    private double getSocialTieValue(DTNHost otherHost) {
        if (!encountersHistory.containsKey(otherHost)) {
            return 0.0;
        }
        double R = 0.0;
        ArrayList<Double> tmp = encountersHistory.get(otherHost);
        double t_base = SimClock.getTime();
        for (double t : tmp) {
            R += Math.pow(1.0 / 2.0, Math.pow(Math.E, -4) * (t_base - t));
            //System.out.printf("%f ", Math.pow(1D / 2, Math.pow(Math.E, -4) * (t_base - t)));
        }

        return R;
    }

    public double getSocialCentrality() {
        double alpha = 0.5;
        double C;
        double C1 = 0.0;
        double C2 = 0.0;
        int N = encountersHistory.size();
        for (Map.Entry<DTNHost, ArrayList<Double>> entry : encountersHistory.entrySet()) {
            C1 += getSocialTieValue(entry.getKey());
        }
        for (Map.Entry<DTNHost, ArrayList<Double>> entry : encountersHistory.entrySet()) {
            C2 += Math.pow(getSocialTieValue(entry.getKey()), 2);
        }
        C = (alpha / N) * C1 + ((1 - alpha) / N) * Math.pow(C1, 2) / C2;
        //System.out.printf("%f ",C);
        return C;
    }

    @Override
    protected Tuple<Message, Connection> tryMessagesForConnected(
            List<Tuple<Message, Connection>> tuples) {
        if (tuples.size() == 0) {
            return null;
        }

        for (Tuple<Message, Connection> t : tuples) {
            Message m = t.getKey();
            Connection con = t.getValue();
            if (startTransfer(m, con) == RCV_OK) {
                return t;
            }
        }

        return null;
    }

    private ArrayList<Tuple<Message, Connection>> socialTheorySort(List<Tuple<Message, Connection>> messages) {
        ArrayList<Tuple<Message, Connection>> forwardList
                = new ArrayList<>();
        double Util = 0.15; //0,.15
        //double percent = 0.01;
        //long total = Math.round(percent * messages.size());
        for (Tuple<Message, Connection> tmp : messages) {
            Message m = tmp.getKey();
            if (m.getTo() != null) {
                DTNHost dest = m.getTo();

                /*Connection c = tmp.getValue();
                DTNHost e = c.getOtherNode(getHost());
                if (((IEEEAccess18RouterLRU) e.getRouter()).getSocialTieValue(dest) > this.getSocialTieValue(dest)) {
                    forwardList.add(new Tuple<Message, Connection>(m, c));
                }*/
                Connection toSend = null;
                EncounterInfo d = fstHopEncounters.get(dest);
                int Sim;
                double Bet, TS;
                if (d != null) {
                    
                    // our similarity to d
                    Sim = d.sim();
                    //System.out.printf("%d ", Sim);
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

                //for(Connection c : getConnections())
                //{
                Connection c = tmp.getValue();
                DTNHost e = c.getOtherNode(getHost());

                EncounterInfo n = fstHopEncounters.get(e);

                EncounterInfo nd = null;
                if (secHopEncounters.get(e) != null) {
                    nd = secHopEncounters.get(e).get(dest);
                }
                //System.out.println("S: " + d.sim() + " D: " + nd.sim());
                double RecI_nd = 0.0;
                double ICI_nd = 0.0;
                double FI_nd = 0.0;

                if (nd != null) {
                    //System.out.println("WTF");
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
                //System.out.println("A: " + SimUtil + " B: " + BetUtil + " C: " + TSUtil);
                double Util_nd = SimUtil + BetUtil + 0 * TSUtil;
                //System.out.printf("%.2f ", Util_nd);
                // see if this node is a better next hop
                if (Util < Util_nd) {
                    Util = Util_nd;
                    toSend = c;
                }
                //}
                if (toSend != null) {                   
                    forwardList.add(new Tuple<Message, Connection>(m, toSend));
                }
                
 /*else if (forwardList.size() < total - 1) {
             forwardList.add(new Tuple<Message, Connection>(m, c));
             } */
            } else { // if(m.getContent() == null || (m.getContent() != null) && m.getContent().getContentType() == ContentType.Interest) {
                Connection c = tmp.getValue();
                /*DTNHost e = c.getOtherNode(getHost());
                if (((IEEEAccess18RouterLRU) e.getRouter()).getSocialCentrality() > this.getSocialCentrality()) {
                    //System.out.println(((JournalRouter) e.getRouter()).getSocialCentrality());
                    forwardList.add(new Tuple<Message, Connection>(m, c));
                }*/

                forwardList.add(new Tuple<Message, Connection>(m, c)); // broadcast
            }
        }

        return (ArrayList) forwardList;
    }

    // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- //
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
        int Ni = fstHopEncounters.size();
        // Adjacency Matrix
        int A[][] = new int[Ni][Ni];

        //
        Object[] fstHopIDs = fstHopEncounters.keySet().toArray();
        // for each contact in our contact set
        for (int i = 0; i < Ni; i++) {
            DTNHost c1 = (DTNHost) fstHopIDs[i];
            // see if they require us to connect them to our other contacts
            for (int j = 0; j < Ni; j++) {
                // dont try to connect them with themselves
                if (i != j) {
                    DTNHost c2 = (DTNHost) fstHopIDs[j];
                    boolean adj = false;
                    if (secHopEncounters.get(c1) != null) {
                        for (Object _c1c : secHopEncounters.get(c1).keySet().toArray()) {
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
        //System.out.println("Betttt: " + b);
        this.EBC = b;

    }

    protected void updateFnDn() {
        Object[] keys = fstHopEncounters.keySet().toArray();
        int _Fn = 0;
        int _Dn = 0;
        for (Object _k : keys) {
            DTNHost k = (DTNHost) _k;
            EncounterInfo h = fstHopEncounters.get(k);
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
        if (fstHopEncounters.containsKey(m)) {
            return fstHopEncounters.get(m).rec();
        } else {
            return 0.0;
        }
    }

    protected double f(DTNHost m, DTNHost D) {
        if (secHopEncounters.get(m).containsKey(D)) {
            return secHopEncounters.get(m).get(D).rec();
        } else {
            return 0.0;
        }
    }

    ////
    // Closeness
    // d(m) = total contact duration with node m
    ////
    protected double d(DTNHost m) {
        if (fstHopEncounters.containsKey(m)) {
            return fstHopEncounters.get(m).rec();
        } else {
            return 0.0;
        }
    }

    ////
    // Recency
    // rec(m) = time since m was last seen
    ////
    protected double rec(DTNHost m) {
        if (fstHopEncounters.containsKey(m)) {
            return fstHopEncounters.get(m).rec();
        } else {
            return 0.0;
        }
    }

    ////
    // Similarity
    // sim(m) = number of contacts in common with m
    ////
    protected double sim(DTNHost m) {
        if (fstHopEncounters.containsKey(m)) {
            return fstHopEncounters.get(m).rec();
        } else {
            return 0.0;
        }
    }

    ////
    // Ego-Betweenness Centrality
    ////
    protected double ebc(DTNHost m) {
        if (fstHopEncounters.containsKey(m)) {
            return fstHopEncounters.get(m).rec();
        } else {
            return 0.0;
        }
    }

    // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- //
    protected class EncounterInfo {

        protected double f;
        protected double d;
        protected double rec;
        protected int sim;
        protected double ebc;
        protected int Fn;
        protected double Dn;
        protected double L;

        public EncounterInfo(double _time) {
            this.f = 0;
            this.d = 0;
            this.rec = _time;
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
