package routing;

import java.util.*;
import util.Tuple;
import core.*;


public class SimBetTS extends ActiveRouter 
{
	/** SprayAndFocus router's settings name space ({@value})*/ 
	public static final String PROTOCOL_NS = "SimBetTS";
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES_S = "nrofCopies";
	/** identifier for the difference in timer values needed to forward on a message copy */
	public static final String TIMER_THRESHOLD_S = "transitivityTimerThreshold";
	/** Message property key for summary vector messages exchanged between direct peers */
	public static final String SUMMARY_XCHG_PROP = "HelloMessage.protoXchg";
	public static final String EBC_XCHG_PROP = "SimBetTS.ebcXchg";
	public static final String Fn_XCHG_PROP = "SimBetTS.FnXchg";
	public static final String Dn_XCHG_PROP = "SimBetTS.DnXchg";
	public static final String L_XCHG_PROP = "SimBetTS.LXchg";
	
	protected static final String SUMMARY_XCHG_IDPREFIX = "summary";
	protected static final double defaultTransitivityThreshold = 60.0;
	protected static int protocolMsgIdx = 0;
	
	
	/** Stores information about nodes with which this host has come in contact */
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
	
	public SimBetTS(Settings s)
	{
		super(s);
		//Settings setts = new Settings(PROTOCOL_NS);
		fstHopEncounters = new HashMap<DTNHost, EncounterInfo>();
		secHopEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();
		
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
	public SimBetTS(SimBetTS r)
	{
		super(r);		
		fstHopEncounters = new HashMap<DTNHost, EncounterInfo>();
		secHopEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();
		
		EBC = 0.0;
		Fn = 0;
		Dn = 0.0;
		TOC = SimClock.getTime();
	}
	
	@Override
	public MessageRouter replicate() 
	{
		return new SimBetTS(this);
	}
	
	public void print(String s){ System.out.println(s); }
	
	
	/**
	 * Called whenever a connection goes up or comes down.
	 */
	@Override
	public void changedConnection(Connection con)
	{
		super.changedConnection(con);
		
		DTNHost thisHost = getHost();
		DTNHost peer = con.getOtherNode(thisHost);
		
		// Do this when con is up and goes down (might have been up for awhile)
		if(fstHopEncounters.containsKey(peer))
		{ 
			EncounterInfo info = fstHopEncounters.get(peer);
			info.updateEncounter(SimClock.getTime(), con.isUp());
			updateFnDn();
		}
		else
		{
			fstHopEncounters.put(peer, new EncounterInfo(SimClock.getTime()));
		}
		
		if(con.isUp())
		{
			// TODO: Update msgSize to reflect SimBetTS contact
			int msgSize = fstHopEncounters.size() * 64 + getMessageCollection().size() * 8;
			
			Message newMsg = new Message(thisHost, peer, SUMMARY_XCHG_IDPREFIX + protocolMsgIdx++, msgSize, null);
			newMsg.addProperty(SUMMARY_XCHG_PROP, fstHopEncounters);
			newMsg.addProperty(EBC_XCHG_PROP, new Double(this.EBC));
			newMsg.addProperty(Fn_XCHG_PROP, new Integer(this.Fn));
			newMsg.addProperty(Dn_XCHG_PROP, new Double(this.Dn));
			newMsg.addProperty(L_XCHG_PROP, new Double(L()));
			
			createNewMessage(newMsg);
		}
	}


	@Override
	public boolean createNewMessage(Message m)
	{
		makeRoomForNewMessage(m.getSize());
		addToMessages(m, true);
		return true;
	}
	
	
	@Override
	public Message messageTransferred(String id, DTNHost from)
	{
		Message m = super.messageTransferred(id, from);
		
		/*
		 * Here we update our last encounter times based on the information sent
		 * from our peer. 
		 */
		Map<DTNHost, EncounterInfo> peerEncounters = (Map<DTNHost, EncounterInfo>)m.getProperty(SUMMARY_XCHG_PROP);
		if(isDeliveredMessage(m) && peerEncounters != null)
		{
			
			/*
			 * We save the peer info for the utility based forwarding decisions, which are
			 * implemented in update()
			 */
			secHopEncounters.put(from, peerEncounters);
			EncounterInfo info = fstHopEncounters.get(from);
			int _sim = similarity(fstHopEncounters.keySet(), secHopEncounters.get(from).keySet());
			info.setSim(_sim);
                        
			info.setStats(((Double)m.getProperty(EBC_XCHG_PROP)).doubleValue(), 
						  ((Integer)m.getProperty(Fn_XCHG_PROP)).intValue(),
						  ((Double)m.getProperty(Dn_XCHG_PROP)).doubleValue(),
						  ((Double)m.getProperty(L_XCHG_PROP)).doubleValue());
			betweenness_update();
			
			return m;
		}
		
		//Normal message beyond here
		
		return m;
	}

	
	@Override
	protected void transferDone(Connection con) 
	{
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
		if(msg.getProperty(SUMMARY_XCHG_PROP) != null)
		{
			deleteMessage(msgId, false);
			return;
		}
		
		
	}
	
	
	@Override
	public void update()
	{
		super.update();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}

		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		List<Tuple<Message,Connection>> forwardlist = new LinkedList<Tuple<Message,Connection>>();

		for (Message m : getMessageCollection())
		{
			if(m.getProperty(SUMMARY_XCHG_PROP) != null) continue;
			
			DTNHost dest = m.getTo();
			Connection toSend = null;
			EncounterInfo d = fstHopEncounters.get(dest);
			int Sim;
			double Bet, TS;
			if (d != null)
			{
				// our similarity to d
				Sim = d.sim();
				
				double FI, ICI, RecI;			
				// Our Tie Strength
				// FI = f(m) / F(n) - f(m)
				if ((this.Fn - d.f()) != 0){
					FI = (double)d.f()/(double)(this.Fn - d.f());
				} else {
					FI = (double)d.f();
				}
				// ICI = d(m) / D(n) - d(m)
				if ((this.Dn - d.d()) != 0) {
					ICI = (double)d.d()/(double)(this.Dn - d.d());
				} else {
					ICI = (double)d.d();
				}
				// RecI = rec(m) / L(n) - rec(m)
				double L = L();
				if ((L - d.rec()) != 0) {
					RecI = (double) d.rec()/(double)(L-d.rec());
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
                        //System.out.println("Bet: " + Bet);
			double Util = 0.5;
			
			for(Connection c : getConnections())
			{
				DTNHost e = c.getOtherNode(getHost());

				EncounterInfo n = fstHopEncounters.get(e);

				EncounterInfo nd = null;
				if (secHopEncounters.get(e) != null)
					nd = secHopEncounters.get(e).get(dest);
					
				double RecI_nd = 0.0;
				double ICI_nd = 0.0;
				double FI_nd = 0.0;
				
				if (nd != null)
				{
					if ((nd.Fn() - nd.f()) != 0){
						FI_nd = (double) nd.f()/(double) (nd.Fn()-nd.f());
					} else {
						FI_nd = (double) nd.f();
					}
					
					if ((nd.Dn()-nd.d()) != 0){
						ICI_nd = (double) nd.d()/(double) (nd.Dn()-nd.d());
					} else {
						ICI_nd = (double) nd.d();
					}
					
					if ((nd.L()-nd.rec()) != 0){
						RecI_nd = (double) nd.rec()/(double) (nd.L()-nd.rec());
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
                                
				if (n != null && (n.ebc() + Bet) != 0)
					BetUtil = (double) n.ebc() / (double) (n.ebc() + Bet);
				
				if ((TS_nd + TS) != 0)
					TSUtil = (double) TS_nd / (double) (TS_nd + TS);
				
				double Util_nd = SimUtil + BetUtil + TSUtil;
				//System.out.println(Util_nd + " A: " + SimUtil + " B: " + BetUtil + " C: " + TSUtil);
				// see if this node is a better next hop
                                //System.out.println(Util_nd);
				if (Util < Util_nd)
				{
					Util = Util_nd;
					toSend = c;
				}
			}
			if (toSend != null)
			{
				forwardlist.add(new Tuple<Message, Connection>(m, toSend));
			}
		}
		
		if(tryMessagesForConnected(forwardlist) != null)
		{
				
		}
	}

	// ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- //
	
	public double L()
	{
		return SimClock.getTime() - TOC;
	}
	
	public int similarity(Set<DTNHost> _A, Set<DTNHost> _B)
	{
		Object[] A = _A.toArray();
		Object[] B = _B.toArray();
		int sim = 0;
		for (Object _a : A) {
			DTNHost a = (DTNHost)_a;
			for (Object _b : B) {
				DTNHost b = (DTNHost)_b;
				if (a == b)
				{
					sim ++;
					break;
				}
			}
		}
		return sim;
	}
	
	
	

	public void betweenness_update ()
	{
		int Ni = fstHopEncounters.size();
		// Adjacency Matrix
		int A [][] = new int[Ni][Ni];
		
		//
		Object[] fstHopIDs = fstHopEncounters.keySet().toArray();
		// for each contact in our contact set
		for (int i = 0; i < Ni; i++)
		{
			DTNHost c1 = (DTNHost)fstHopIDs[i];
			// see if they require us to connect them to our other contacts
			for (int j = 0; j < Ni; j++)
			{
				// dont try to connect them with themselves
				if (i != j)
				{
					DTNHost c2 = (DTNHost)fstHopIDs[j];
					boolean adj = false;
					if (secHopEncounters.get(c1) != null){
						for (Object _c1c : secHopEncounters.get(c1).keySet().toArray())
						{
							DTNHost c1c = (DTNHost)_c1c;
							if (c1c == c2)
							{
								adj = true;
								break;
							}
						}
					}
					if (adj)
						A[i][j] = 1;
					else
						A[i][j] = 0;	
				} else {
					A[i][j] = 0;
				}
			}
		}
		
		// A^2
		double A2 [][] = new double[Ni][Ni];;
		for (int i = 0; i < Ni; i++)
		{
			for (int j = 0; j < Ni; j++)
			{
				A2[i][j] = 0;
				for (int k = 0; k < Ni; k++)
				{
					A2[i][j] += A[i][k] * A[k][j];
				}
			}
		}
		
		// A^2[1-A]ij
		double A3 [][] = new double[Ni][Ni];;
		for (int i = 0; i < Ni; i++)
		{
			for (int j = 0; j < Ni; j++)
			{
				if (A2[i][j] != 0)
					A3[i][j] = 1 / A2[i][j];
			}
		}
		
		// Sum of values for dependant contacts
		double b = 0.0;
		for (int i = 0; i < Ni; i++)
		{
			for (int j = i+1; j < Ni; j++)
			{
				if (A[i][j] == 0)
					b += A3[i][j];
			}
		}
		//System.out.println("Betttt: " + b);
		this.EBC = b;
		
	}
	
	protected void updateFnDn()
	{
		Object[] keys = fstHopEncounters.keySet().toArray();
		int _Fn = 0;
		int _Dn = 0;
		for (Object _k : keys)
		{
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
	protected double f(DTNHost m)
	{
		if(fstHopEncounters.containsKey(m))
			return fstHopEncounters.get(m).rec();
		else
			return 0.0;
	}
	protected double f(DTNHost m, DTNHost D)
	{
		if(secHopEncounters.get(m).containsKey(D))
			return secHopEncounters.get(m).get(D).rec();
		else
			return 0.0;
	}
	

	////
	// Closeness
	// d(m) = total contact duration with node m
	////
	protected double d(DTNHost m)
	{
		if(fstHopEncounters.containsKey(m))
			return fstHopEncounters.get(m).rec();
		else
			return 0.0;
	}
	

	////
	// Recency
	// rec(m) = time since m was last seen
	////
	protected double rec(DTNHost m)
	{
		if(fstHopEncounters.containsKey(m))
			return fstHopEncounters.get(m).rec();
		else
			return 0.0;
	}

	////
	// Similarity
	// sim(m) = number of contacts in common with m
	////
	protected double sim(DTNHost m)
	{
		if(fstHopEncounters.containsKey(m))
			return fstHopEncounters.get(m).rec();
		else
			return 0.0;
	}
	
	////
	// Ego-Betweenness Centrality
	////
	protected double ebc(DTNHost m)
	{
		if(fstHopEncounters.containsKey(m))
			return fstHopEncounters.get(m).rec();
		else
			return 0.0;
	}
	
	
	// ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- // ---- //
	
	
	protected class EncounterInfo
	{
		protected double f;
		protected double d;
		protected double rec;
		protected int sim;
		protected double ebc;
		protected int Fn;
		protected double Dn;
		protected double L;
		
		public EncounterInfo(double _time)
		{
			this.f = 0;
			this.d = 0;
			this.rec = _time;
		}
		
		public void updateEncounter(double _time, boolean _isUp)
		{
			if (!_isUp)
			{
				this.f ++;
				this.d += _time - this.rec;
			}
			this.rec = _time;
			
		}
		
		public void setSim(int _sim)
		{
			this.sim = _sim;
		}
		
		public void setStats(double _ebc, int _Fn, double _Dn, double _L)
		{
			this.ebc = _ebc;
			this.Fn = _Fn;
			this.Dn = _Dn;
			this.L = _L;
		}
		
		public double f()
		{
			return f;
		}
		public double d()
		{
			return d;
		}
		public double rec()
		{
			return rec;
		}
		public int sim()
		{
			return sim;
		}		
		public double ebc()
		{
			return ebc;
		}
		public double Fn()
		{
			return Fn;
		}
		public double Dn()
		{
			return Dn;
		}
		public double L()
		{
			return L;
		}
		
	}

}
