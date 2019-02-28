package report;

import core.ConnectionListener;
import core.DTNHost;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class RankConnectivityBasedOnTimes extends Report implements ConnectionListener {

    Map<Integer, Integer> connectivityTimesCounter;

    public RankConnectivityBasedOnTimes() {
        init();
        this.connectivityTimesCounter = new HashMap();
    }

    public void hostsConnected(DTNHost h1, DTNHost h2) {
        if (isWarmup()) {
            addWarmupID(connectionString(h1, h2));
            return;
        }
        newEvent();
        if (this.connectivityTimesCounter.get(Integer.valueOf(h1.getAddress())) == null) {
            this.connectivityTimesCounter.put(Integer.valueOf(h1.getAddress()), Integer.valueOf(0));
        } else {
            this.connectivityTimesCounter.put(Integer.valueOf(h1.getAddress()), Integer.valueOf(((Integer) this.connectivityTimesCounter.get(Integer.valueOf(h1.getAddress()))).intValue() + 1));
        }
        if (this.connectivityTimesCounter.get(Integer.valueOf(h2.getAddress())) == null) {
            this.connectivityTimesCounter.put(Integer.valueOf(h2.getAddress()), Integer.valueOf(0));
        } else {
            this.connectivityTimesCounter.put(Integer.valueOf(h2.getAddress()), Integer.valueOf(((Integer) this.connectivityTimesCounter.get(Integer.valueOf(h2.getAddress()))).intValue() + 1));
        }
    }

    public void hostsDisconnected(DTNHost h1, DTNHost h2) {
        String conString = connectionString(h1, h2);
        if ((isWarmup()) || (isWarmupID(conString))) {
            removeWarmupID(conString);
            return;
        }
    }

    private String createTimeStamp() {
        return String.format("%.2f", new Object[]{Double.valueOf(getSimTime())});
    }

    private String connectionString(DTNHost h1, DTNHost h2) {
        if (h1.getAddress() < h2.getAddress()) {
            return h1.getAddress() + " " + h2.getAddress();
        }
        return h2.getAddress() + " " + h1.getAddress();
    }

    public void done() {
        Map<Integer, Integer> sortedMap = sortByValue(this.connectivityTimesCounter);
        for (Map.Entry<Integer, Integer> entry : sortedMap.entrySet()) {
            write(entry.getKey() + " " + entry.getValue());
        }
        System.out.println("Completed");
        super.done();
    }

    private static Map<Integer, Integer> sortByValue(Map<Integer, Integer> unsortedMap) {
        List<Map.Entry<Integer, Integer>> list = new LinkedList(unsortedMap.entrySet());
        
        Collections.sort(list, new Comparator<Entry<Integer, Integer>>() {
            public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                return o1.getValue() == o2.getValue() ? 0 : ((Integer) o1.getValue()).intValue() < ((Integer) o2.getValue()).intValue() ? 1 : -1;
            }
        });
        Map<Integer, Integer> sortedMap = new LinkedHashMap();
        for (Map.Entry<Integer, Integer> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
    }
}
