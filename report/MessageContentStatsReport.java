/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package report;

import core.Content;
import core.ContentType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.DTNHost;
import core.Message;
import core.MessageListener;

/**
 * Report for generating different kind of total statistics about message
 * relaying performance. Messages that were created during the warm up period
 * are ignored.
 * <P>
 * <strong>Note:</strong> if some statistics could not be created (e.g. overhead
 * ratio if no messages were delivered) "NaN" is reported for double values and
 * zero for integer median(s).
 */
public class MessageContentStatsReport extends Report implements MessageListener {

    private Map<String, Double> creationTimes;
    private List<Double> latencies;
    private List<Integer> hopCounts;
    private List<Double> msgBufferTime;
    private List<Double> rtt; // round trip times

    private int nrofDropped;
    private int nrofRemoved;
    private int nrofStarted;
    private int nrofAborted;
    private int nrofRelayed;
    private int nrofCreated;
    private int nrofResponseReqCreated;
    private int nrofResponseDelivered;
    private int nrofDelivered;

    private int nrofDroppedCacheHit;
    private int nrofRemovedCacheHit;
    private int nrofStartedCacheHit;
    private int nrofAbortedCacheHit;
    private int nrofRelayedCacheHit;
    private int nrofDeliveredCacheHit;
    private Map<String, Double> creationTimesCacheHit;
    private List<Double> latenciesCacheHit;
    private List<Integer> hopCountsCacheHit;

    private int nrofDroppedCacheMiss;
    private int nrofRemovedCacheMiss;
    private int nrofStartedCacheMiss;
    private int nrofAbortedCacheMiss;
    private int nrofRelayedCacheMiss;
    private int nrofDeliveredCacheMiss;
    private Map<String, Double> creationTimesCacheMiss;
    private List<Double> latenciesCacheMiss;
    private List<Integer> hopCountsCacheMiss;

    private List<Double> interestLatencies;
    private List<Integer> interestHopCounts;
    private List<Double> interestMsgBufferTime;
    private List<Double> interestRtt; // round trip times

    private int interestNrofDropped;
    private int interestNrofRemoved;
    private int interestNrofStarted;
    private int interestNrofAborted;
    private int interestNrofRelayed;
    private int interestNrofCreated;
    private int interestNrofResponseReqCreated;
    private int interestNrofResponseDelivered;
    private int interestNrofDelivered;

    private List<Double> advertisementLatencies;
    private List<Integer> advertisementHopCounts;
    private List<Double> advertisementMsgBufferTime;
    private List<Double> advertisementRtt; // round trip times

    private int advertisementNrofDropped;
    private int advertisementNrofRemoved;
    private int advertisementNrofStarted;
    private int advertisementNrofAborted;
    private int advertisementNrofRelayed;
    private int advertisementNrofCreated;
    private int advertisementNrofResponseReqCreated;
    private int advertisementNrofResponseDelivered;
    private int advertisementNrofDelivered;

    private HashMap<String, Content> matchedMessages;
    private HashMap<Integer, String> deliveredMessages;
    private HashMap<Integer, Integer> createdContents;
    private HashMap<Integer, Integer> deliveredContents;
    private HashMap<Integer, ArrayList<Double>> delayContents;
    private HashMap<Integer, Integer> duplicatedMatches;

    private int numCacheHit;
    private int numCacheMiss;
    private List<Double> latenciesInterestCacheHit;
    private List<Double> latenciesInterestCacheMiss;

    /**
     * Constructor.
     */
    public MessageContentStatsReport() {
        init();
    }

    @Override
    protected void init() {
        super.init();
        this.creationTimes = new HashMap<String, Double>();
        this.latencies = new ArrayList<Double>();
        this.msgBufferTime = new ArrayList<Double>();
        this.hopCounts = new ArrayList<Integer>();
        this.rtt = new ArrayList<Double>();

        this.nrofDropped = 0;
        this.nrofRemoved = 0;
        this.nrofStarted = 0;
        this.nrofAborted = 0;
        this.nrofRelayed = 0;
        this.nrofCreated = 0;
        this.nrofResponseReqCreated = 0;
        this.nrofResponseDelivered = 0;
        this.nrofDelivered = 0;
        this.numCacheHit = 0;
        this.numCacheMiss = 0;

        this.nrofDroppedCacheHit = 0;
        this.nrofRemovedCacheHit = 0;
        this.nrofStartedCacheHit = 0;
        this.nrofAbortedCacheHit = 0;
        this.nrofRelayedCacheHit = 0;
        this.nrofDeliveredCacheHit = 0;
        this.creationTimesCacheHit = new HashMap<String, Double>();
        this.latenciesCacheHit = new ArrayList<Double>();
        this.hopCountsCacheHit = new ArrayList<Integer>();

        this.nrofDroppedCacheMiss = 0;
        this.nrofRemovedCacheMiss = 0;
        this.nrofStartedCacheMiss = 0;
        this.nrofAbortedCacheMiss = 0;
        this.nrofRelayedCacheMiss = 0;
        this.nrofDeliveredCacheMiss = 0;
        this.creationTimesCacheMiss = new HashMap<String, Double>();
        this.latenciesCacheMiss = new ArrayList<Double>();
        this.hopCountsCacheMiss = new ArrayList<Integer>();

        this.interestLatencies = new ArrayList<Double>();
        this.interestMsgBufferTime = new ArrayList<Double>();
        this.interestHopCounts = new ArrayList<Integer>();
        this.interestRtt = new ArrayList<Double>();

        this.interestNrofDropped = 0;
        this.interestNrofRemoved = 0;
        this.interestNrofStarted = 0;
        this.interestNrofAborted = 0;
        this.interestNrofRelayed = 0;
        this.interestNrofCreated = 0;
        this.interestNrofResponseReqCreated = 0;
        this.interestNrofResponseDelivered = 0;
        this.interestNrofDelivered = 0;
        this.latenciesInterestCacheHit = new ArrayList<Double>();
        this.latenciesInterestCacheMiss = new ArrayList<Double>();

        this.advertisementLatencies = new ArrayList<Double>();
        this.advertisementMsgBufferTime = new ArrayList<Double>();
        this.advertisementHopCounts = new ArrayList<Integer>();
        this.advertisementRtt = new ArrayList<Double>();

        this.advertisementNrofDropped = 0;
        this.advertisementNrofRemoved = 0;
        this.advertisementNrofStarted = 0;
        this.advertisementNrofAborted = 0;
        this.advertisementNrofRelayed = 0;
        this.advertisementNrofCreated = 0;
        this.advertisementNrofResponseReqCreated = 0;
        this.advertisementNrofResponseDelivered = 0;
        this.advertisementNrofDelivered = 0;

        this.matchedMessages = new HashMap<>();
        this.deliveredMessages = new HashMap<>();
        this.createdContents = new HashMap<>();
        this.deliveredContents = new HashMap<>();
        this.delayContents = new HashMap<>();
        this.duplicatedMatches = new HashMap<>();
    }

    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
        if (isWarmupID(m.getId())) {
            return;
        }
        if (isHelloMessage(m)) {
            return;
        }

        if (dropped) {
            if (m.getContent().getContentType() == ContentType.Interest) {
                this.interestNrofDropped++;
            } else if (m.getContent().getContentType() == ContentType.Advert) {
                this.advertisementNrofDropped++;
            } else if (m.getContent().getContentType() == ContentType.Content) {
                this.nrofDropped++;
                if (!m.getFrom().hasPublishedContent(m.getContent())) {
                    this.nrofDroppedCacheHit++;
                } else {
                    this.nrofDroppedCacheHit++;
                }
            }
        } else {
            if (m.getContent().getContentType() == ContentType.Interest) {
                this.interestNrofRemoved++;
            } else if (m.getContent().getContentType() == ContentType.Advert) {
                this.advertisementNrofRemoved++;
            } else if (m.getContent().getContentType() == ContentType.Content) {
                this.nrofRemoved++;
                if (!m.getFrom().hasPublishedContent(m.getContent())) {
                    this.nrofRemovedCacheHit++;
                } else {
                    this.nrofRemovedCacheMiss++;
                }
            }
        }
        if (m.getContent().getContentType() == ContentType.Interest) {
            this.interestMsgBufferTime.add(getSimTime() - m.getReceiveTime());
        } else if (m.getContent().getContentType() == ContentType.Advert) {
            this.advertisementMsgBufferTime.add(getSimTime() - m.getReceiveTime());
        } else if (m.getContent().getContentType() == ContentType.Content) {
            this.msgBufferTime.add(getSimTime() - m.getReceiveTime());
        }
    }

    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
        if (isWarmupID(m.getId())) {
            return;
        }
        if (isHelloMessage(m)) {
            return;
        }
        if (m.getContent().getContentType() == ContentType.Interest) {
            this.interestNrofAborted++;
        } else if (m.getContent().getContentType() == ContentType.Advert) {
            this.advertisementNrofAborted++;
        } else if (m.getContent().getContentType() == ContentType.Content) {
            this.nrofAborted++;
            if (!m.getFrom().hasPublishedContent(m.getContent())) {
                this.nrofAbortedCacheHit++;
            } else {
                this.nrofAbortedCacheMiss++;
            }
        }

    }

    public void messageTransferred(Message m, DTNHost from, DTNHost to,
            boolean finalTarget) {
        if (isWarmupID(m.getId())) {
            return;
        }
        if (isHelloMessage(m)) {
            return;
        }
        if (m.getContent().getContentType() == ContentType.Interest) {
            this.interestNrofRelayed++;
        } else if (m.getContent().getContentType() == ContentType.Advert) {
            this.advertisementNrofRelayed++;
        } else if (m.getContent().getContentType() == ContentType.Content) {
            this.nrofRelayed++;
            if (!m.getFrom().hasPublishedContent(m.getContent())) {
                this.nrofRelayedCacheHit++;
            } else {
                this.nrofRelayedCacheMiss++;
            }
        }

        if (finalTarget) {

            if (m.getContent().getContentType() == ContentType.Interest) {
                this.interestLatencies.add(getSimTime()
                        - this.creationTimes.get(m.getId()));
                this.interestNrofDelivered++;
                this.interestHopCounts.add(m.getHops().size() - 1);

                if (m.isResponse()) {
                    this.interestRtt.add(getSimTime() - m.getRequest().getCreationTime());
                    this.interestNrofResponseDelivered++;
                }
            } else if (m.getContent().getContentType() == ContentType.Advert) {
                this.advertisementLatencies.add(getSimTime()
                        - this.creationTimes.get(m.getId()));
                this.advertisementNrofDelivered++;
                this.advertisementHopCounts.add(m.getHops().size() - 1);

                if (m.isResponse()) {
                    this.advertisementRtt.add(getSimTime() - m.getRequest().getCreationTime());
                    this.advertisementNrofResponseDelivered++;
                }
            } else if (m.getContent().getContentType() == ContentType.Content) {
                this.latencies.add(getSimTime()
                        - this.creationTimes.get(m.getId()));
                this.nrofDelivered++;
                this.hopCounts.add(m.getHops().size() - 1);

                if (!deliveredContents.containsKey(m.getContent().getContentId())) {
                    deliveredContents.put(m.getContent().getContentId(), 1);
                } else {
                    int count = deliveredContents.get(m.getContent().getContentId());
                    count++;
                    deliveredContents.put(m.getContent().getContentId(), count);
                }
                if (!delayContents.containsKey(m.getContent().getContentId())) {
                    ArrayList<Double> tmp = new ArrayList<Double>();
                    tmp.add(getSimTime()
                            - this.creationTimes.get(m.getId()));
                    delayContents.put(m.getContent().getContentId(), tmp);
                } else {
                    ArrayList<Double> tmp = delayContents.get(m.getContent().getContentId());
                    tmp.add(getSimTime() - this.creationTimes.get(m.getId()));
                    delayContents.put(m.getContent().getContentId(), tmp);
                }
                if (m.isResponse()) {
                    this.rtt.add(getSimTime() - m.getRequest().getCreationTime());
                    this.nrofResponseDelivered++;
                }
                if (!m.getFrom().hasPublishedContent(m.getContent())) {
                    //System.out.println("-" + m.getId() + "-" + m.getContent().getContentId());
                    if (this.creationTimesCacheHit.get(m.getId()) != null) {
                        this.latenciesCacheHit.add(getSimTime()
                                - this.creationTimesCacheHit.get(m.getId()));
                        this.nrofDeliveredCacheHit++;
                        this.hopCountsCacheHit.add(m.getHops().size() - 1);
                    } else { // Interest is created as cache miss, but the interest in cache hit delivered first.
                        this.latenciesCacheMiss.add(getSimTime()
                                - this.creationTimes.get(m.getId()));
                        this.nrofDeliveredCacheMiss++;
                        this.hopCountsCacheMiss.add(m.getHops().size() - 1);
                    }

                } else {
                    if (this.creationTimesCacheMiss.get(m.getId()) != null) {
                        this.latenciesCacheMiss.add(getSimTime()
                                - this.creationTimesCacheMiss.get(m.getId()));
                        this.nrofDeliveredCacheMiss++;
                        this.hopCountsCacheMiss.add(m.getHops().size() - 1);
                    } else { // the content not published by the host, interest meets message containing content => assume cache hit 
                        this.latenciesCacheHit.add(getSimTime()
                                - this.creationTimes.get(m.getId()));
                        this.nrofDeliveredCacheHit++;
                        this.hopCountsCacheHit.add(m.getHops().size() - 1);
                    }
                }
            }
        }
    }

    public boolean setDeliveredMessages(int hostAddress, String interestID) {
        if (!deliveredMessages.containsKey(hostAddress) || deliveredMessages.get(hostAddress) != interestID) {
            this.deliveredMessages.put(hostAddress, interestID);
            return true;
        } else {
            return false;
        }
    }

    public boolean setMatchedMessages(Message m) {
        if (!matchedMessages.containsKey(m.getId())) {
            //System.out.println("Hello: " + m.getId());
            this.matchedMessages.put(m.getId(), m.getContent());
            return true;
        } else {
             if (!duplicatedMatches.containsKey(m.getContent().getContentId())) {
                    duplicatedMatches.put(m.getContent().getContentId(), 1);
                } else {
                    int tmp = duplicatedMatches.get(m.getContent().getContentId());
                    duplicatedMatches.put(m.getContent().getContentId(), tmp+1);
                }
            return false;
        }
    }

    public void setAdvertisementStats(Message interest, Message m) {
        if (!matchedMessages.containsKey(interest.getId())) {
            //System.out.println(m.getId());
            this.advertisementLatencies.add(getSimTime()
                    - this.creationTimes.get(m.getId()));
            this.advertisementNrofDelivered++;
            this.advertisementHopCounts.add(m.getHops().size() - 1);

            if (m.isResponse()) {
                this.advertisementRtt.add(getSimTime() - m.getRequest().getCreationTime());
                this.advertisementNrofResponseDelivered++;
            }
        }
    }

    public void setInterestStats(Message m) {
        if (!matchedMessages.containsKey(m.getId())) {
            //System.out.println(m.getId());
            this.interestLatencies.add(getSimTime()
                    - this.creationTimes.get(m.getId()));
            this.interestNrofDelivered++;
            this.interestHopCounts.add(m.getHops().size() - 1);

            if (m.isResponse()) {
                this.interestRtt.add(getSimTime() - m.getRequest().getCreationTime());
                this.interestNrofResponseDelivered++;
            }
            this.latenciesInterestCacheHit.add(getSimTime()
                    - this.creationTimes.get(m.getId()));

        }
    }

    public void setCacheHitStats(Message m, boolean isCacheHit) {
        if (isCacheHit) {
            numCacheHit++;
            this.latenciesInterestCacheHit.add(getSimTime()
                    - this.creationTimes.get(m.getId()));
            //System.out.println("+" +m.getId().replace("_interest", "").replace("_advert", ""));
            this.creationTimesCacheHit.put(m.getId().replace("_interest", "").replace("_advert", ""), getSimTime());

        } else {
            numCacheMiss++;
            this.latenciesInterestCacheMiss.add(getSimTime()
                    - this.creationTimes.get(m.getId()));
            //System.out.println("." +m.getId().replace("_interest", "").replace("_advert", "") + "." + m.getContent().getContentId());
            this.creationTimesCacheMiss.put(m.getId().replace("_interest", "").replace("_advert", ""), getSimTime());

        }
    }

    public void newMessage(Message m) {
        if (isWarmup()) {
            addWarmupID(m.getId());
            return;
        }
        if (isHelloMessage(m)) {
            return;
        }

        this.creationTimes.put(m.getId(), getSimTime());

        if (m.getContent().getContentType() == ContentType.Interest) {
            this.interestNrofCreated++;
            if (m.getResponseSize() > 0) {
                this.interestNrofResponseReqCreated++;
            }
            if (!createdContents.containsKey(m.getContent().getContentId())) {
                createdContents.put(m.getContent().getContentId(), 1);
            } else {
                int count = createdContents.get(m.getContent().getContentId());
                count++;
                createdContents.put(m.getContent().getContentId(), count);
            }
        } else if (m.getContent().getContentType() == ContentType.Advert) {
            this.advertisementNrofCreated++;
            if (m.getResponseSize() > 0) {
                this.advertisementNrofResponseReqCreated++;
            }
        } else if (m.getContent().getContentType() == ContentType.Content) {
            this.nrofCreated++;
            if (m.getResponseSize() > 0) {
                this.nrofResponseReqCreated++;
            }
        }
    }

    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
        if (isWarmupID(m.getId())) {
            return;
        }
        if (isHelloMessage(m)) {
            return;
        }
        if (m.getContent().getContentType() == ContentType.Interest) {
            this.interestNrofStarted++;
        } else if (m.getContent().getContentType() == ContentType.Advert) {
            this.advertisementNrofStarted++;
        } else if (m.getContent().getContentType() == ContentType.Content) {
            this.nrofStarted++;
            if (!m.getFrom().hasPublishedContent(m.getContent())) {
                this.nrofStartedCacheHit++;
            } else {
                this.nrofStartedCacheMiss++;
            }
        }
    }

    @Override
    public void done() {
        write("Message stats for scenario " + getScenarioName()
                + "\nsim_time: " + format(getSimTime()));
        double deliveryProb = 0; // delivery probability
        double responseProb = 0; // request-response success probability
        double overHead = Double.NaN;	// overhead ratio

        double cacheHitProb = (double) numCacheHit / this.interestNrofDelivered;
        double interestDeliveryProb = 0; // delivery probability
        double interestResponseProb = 0; // request-response success probability
        double interestOverHead = Double.NaN;	// overhead ratio

        double advertisementDeliveryProb = 0; // delivery probability
        double advertisementResponseProb = 0; // request-response success probability
        double advertisementOverHead = Double.NaN;	// overhead ratio

        double contentDeliveryProb = 0;
        double contentRetrievalProb = 0;

        if (this.nrofCreated > 0) {
            deliveryProb = (1.0 * this.nrofDelivered) / this.nrofCreated;
        }
        if (this.interestNrofCreated > 0) {
            interestDeliveryProb = (1.0 * this.interestNrofDelivered) / this.interestNrofCreated;
        }
        if (this.nrofDelivered > 0) {
            contentDeliveryProb = (1.0 * this.nrofDelivered)
                    / this.interestNrofDelivered;
        }
        if (this.nrofDelivered > 0) {
            contentRetrievalProb = (1.0 * this.nrofDelivered)
                    / this.interestNrofCreated;
        }
        if (this.advertisementNrofCreated > 0) {
            advertisementDeliveryProb = (1.0 * this.advertisementNrofDelivered) / this.advertisementNrofCreated;
        }
        if (this.nrofDelivered > 0) {
            overHead = (1.0 * (this.nrofRelayed - this.nrofDelivered))
                    / this.nrofDelivered;
        }
        if (this.interestNrofDelivered > 0) {
            interestOverHead = (1.0 * (this.interestNrofRelayed - this.interestNrofDelivered))
                    / this.interestNrofDelivered;
        }
        if (this.advertisementNrofDelivered > 0) {
            advertisementOverHead = (1.0 * (this.advertisementNrofRelayed - this.advertisementNrofDelivered))
                    / this.advertisementNrofDelivered;
        }
        if (this.nrofResponseReqCreated > 0) {
            responseProb = (1.0 * this.nrofResponseDelivered)
                    / this.nrofResponseReqCreated;
        }
        if (this.interestNrofResponseReqCreated > 0) {
            interestResponseProb = (1.0 * this.interestNrofResponseDelivered)
                    / this.interestNrofResponseReqCreated;
        }
        if (this.advertisementNrofResponseReqCreated > 0) {
            advertisementResponseProb = (1.0 * this.advertisementNrofResponseDelivered)
                    / this.advertisementNrofResponseReqCreated;
        }

        String statsText = "created: " + this.nrofCreated
                + "\nstarted: " + this.nrofStarted
                + "\nrelayed: " + this.nrofRelayed
                + "\naborted: " + this.nrofAborted
                + "\ndropped: " + this.nrofDropped
                + "\nremoved: " + this.nrofRemoved
                + "\ndelivered: " + this.nrofDelivered
                + "\ndelivery_prob: " + format(deliveryProb)
                + "\nresponse_prob: " + format(responseProb)
                + "\noverhead_ratio: " + format(overHead)
                + "\nlatency_avg: " + getAverage(this.latencies)
                + "\nlatency_med: " + getMedian(this.latencies)
                + "\npacket_loss1: " + format(1.0*this.nrofDropped/this.nrofStarted)
                + "\npacket_loss2: " + format(1.0*(this.nrofStarted - this.nrofDropped - this.nrofRemoved)/this.nrofStarted)
                + "\nhopcount_avg: " + getIntAverage(this.hopCounts)
                + "\nhopcount_med: " + getIntMedian(this.hopCounts)
                + "\nbuffertime_avg: " + getAverage(this.msgBufferTime)
                + "\nbuffertime_med: " + getMedian(this.msgBufferTime)
                + "\nrtt_avg: " + getAverage(this.rtt)
                + "\nrtt_med: " + getMedian(this.rtt)
                + "\n"
                + "\nInterest Created: " + this.interestNrofCreated
                + "\nInterest Started: " + this.interestNrofStarted
                + "\nInterest Relayed: " + this.interestNrofRelayed
                + "\nInterest Aborted: " + this.interestNrofAborted
                + "\nInterest Dropped: " + this.interestNrofDropped
                + "\nInterest Removed: " + this.interestNrofRemoved
                + "\nCache Hit: " + this.numCacheHit
                + "\nCache Miss: " + this.numCacheMiss
                + "\nCache Hit Prob/Created: " + format((double) numCacheHit / this.interestNrofCreated)
                + "\nCache Miss Prob/Created: " + format((double) numCacheMiss / this.interestNrofCreated)
                + "\nCache Hit Prob/Delivered: " + format(cacheHitProb)
                + "\nInterest Delivered: " + this.interestNrofDelivered
                + "\nInterest Delivery_prob: " + format(interestDeliveryProb)
                + "\nContent Delivery_prob: " + format(contentDeliveryProb)
                + "\nContent Retrieval_prob: " + format(contentRetrievalProb)
                + "\nInterest Response_prob: " + format(interestResponseProb)
                + "\nInterest Overhead_ratio: " + format(interestOverHead)
                + "\nInterest Latency_avg: " + getAverage(this.interestLatencies)
                + "\nInterest Latency_med: " + getMedian(this.interestLatencies)
                + "\nInterest Packet_loss1: " + format(1.0*this.interestNrofDropped/this.interestNrofStarted)
                + "\nInterest Packet_loss2: " + format(1.0*(this.interestNrofStarted - this.interestNrofDropped - this.interestNrofRemoved)/this.interestNrofStarted)
                + "\nInterest Hopcount_avg: " + getIntAverage(this.interestHopCounts)
                + "\nInterest Hopcount_med: " + getIntMedian(this.interestHopCounts)
                + "\nInterest Buffertime_avg: " + getAverage(this.interestMsgBufferTime)
                + "\nInterest Buffertime_med: " + getMedian(this.interestMsgBufferTime)
                + "\nInterest Rtt_avg: " + getAverage(this.interestRtt)
                + "\nInterest Rtt_med: " + getMedian(this.interestRtt)
                + "\n"
                + "\nCacheHit started: " + this.nrofStartedCacheHit
                + "\nCacheHit relayed: " + this.nrofRelayedCacheHit
                + "\nCacheHit aborted: " + this.nrofAbortedCacheHit
                + "\nCacheHit dropped: " + this.nrofDroppedCacheHit
                + "\nCacheHit removed: " + this.nrofRemovedCacheHit
                + "\nCacheHit delivered: " + this.nrofDeliveredCacheHit
                + "\nCacheHit delivery_prob: " + format((1.0 * this.nrofDeliveredCacheHit) / this.numCacheHit)
                + "\nCacheHit latency_avg: " + getAverage(this.latenciesCacheHit)
                + "\nCacheHit latency_interest_avg: " + getAverage(this.latenciesInterestCacheHit)
                + "\nCacheHit latency_med: " + getMedian(this.latenciesCacheHit)
                + "\nCacheHit packet_loss1: " + format(1.0 * this.nrofDroppedCacheHit/this.nrofStartedCacheHit)
                + "\nCacheHit packet_loss2: " + format(1.0 * (this.nrofStartedCacheHit - this.nrofDroppedCacheHit - this.nrofRemovedCacheHit)/this.nrofStartedCacheHit)
                + "\nCacheHit hopcount_avg: " + getIntAverage(this.hopCountsCacheHit)
                + "\n"
                + "\nCacheMiss started: " + this.nrofStartedCacheMiss
                + "\nCacheMiss relayed: " + this.nrofRelayedCacheMiss
                + "\nCacheMiss aborted: " + this.nrofAbortedCacheMiss
                + "\nCacheMiss dropped: " + this.nrofDroppedCacheMiss
                + "\nCacheMiss removed: " + this.nrofRemovedCacheMiss
                + "\nCacheMiss delivered: " + this.nrofDeliveredCacheMiss
                + "\nCacheMiss delivery_prob: " + format((1.0 * this.nrofDeliveredCacheMiss) / this.numCacheMiss)
                + "\nCacheMiss latency_avg: " + getAverage(this.latenciesCacheMiss)
                + "\nCacheMiss latency_interest_avg: " + getAverage(this.latenciesInterestCacheMiss)
                + "\nCacheMiss latency_med: " + getMedian(this.latenciesCacheMiss)
                + "\nCacheMiss packet_loss1: " + format(1.0*this.nrofDroppedCacheMiss/this.nrofStartedCacheMiss)
                + "\nCacheMiss packet_loss2: " + format(1.0*(this.nrofStartedCacheMiss - this.nrofDroppedCacheMiss - this.nrofRemovedCacheMiss)/this.nrofStartedCacheMiss)
                + "\nCacheMiss hopcount_avg: " + getIntAverage(this.hopCountsCacheMiss)
                + "\n"
                + "\nAdvertisement Created: " + this.advertisementNrofCreated
                + "\nAdvertisement Started: " + this.advertisementNrofStarted
                + "\nAdvertisement Relayed: " + this.advertisementNrofRelayed
                + "\nAdvertisement Aborted: " + this.advertisementNrofAborted
                + "\nAdvertisement Dropped: " + this.advertisementNrofDropped
                + "\nAdvertisement Removed: " + this.advertisementNrofRemoved
                + "\nAdvertisement Delivered: " + this.advertisementNrofDelivered
                + "\nAdvertisement Delivery_prob: " + format(advertisementDeliveryProb)
                + "\nAdvertisement Response_prob: " + format(advertisementResponseProb)
                + "\nAdvertisement Overhead_ratio: " + format(advertisementOverHead)
                + "\nAdvertisement Latency_avg: " + getAverage(this.advertisementLatencies)
                + "\nAdvertisement Latency_med: " + getMedian(this.advertisementLatencies)
                + "\nAdvertisement Hopcount_avg: " + getIntAverage(this.advertisementHopCounts)
                + "\nAdvertisement Hopcount_med: " + getIntMedian(this.advertisementHopCounts)
                + "\nAdvertisement Buffertime_avg: " + getAverage(this.advertisementMsgBufferTime)
                + "\nAdvertisement Buffertime_med: " + getMedian(this.advertisementMsgBufferTime)
                + "\nAdvertisement Rtt_avg: " + getAverage(this.advertisementRtt)
                + "\nAdvertisement Rtt_med: " + getMedian(this.advertisementRtt)
                + "\n\n";

        statsText += "contentID, # requests, # delivered, % success, delay, # duplicates";
        /*for (Integer key : createdContents.keySet()) {
            statsText += "\n" + key.toString() + " " + createdContents.get(key).toString();
        }
        statsText += "\n\n";

        for (Integer key : deliveredContents.keySet()) {
            double sr = (double) deliveredContents.get(key) / (double) createdContents.get(key);
            statsText += "\n" + key.toString() + " " + (double) deliveredContents.get(key) + " " + sr + " " + getAverage(delayContents.get(key)).toString();
        }
        statsText += "\n\n";*/
        for (Integer key : createdContents.keySet()) {
            if(deliveredContents.containsKey(key)) {
                double successRatio = (double) deliveredContents.get(key) / (double) createdContents.get(key);
                statsText += "\n" + key.toString() + ", " + createdContents.get(key).toString() + ", " + (double) deliveredContents.get(key) + ", " + successRatio + ", " + getAverage(delayContents.get(key)).toString() + ", " + duplicatedMatches.get(key);
            } else {
                statsText += "\n" + key.toString() + ", " + createdContents.get(key).toString() + ", " + 0 + ", " + 0.0 + ", " + 0.0 + ", " + duplicatedMatches.get(key);
            }       
        }

        write(statsText);
        //System.out.println(statsText);
        super.done();
    }

}
