/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package input;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;
import javafx.collections.transformation.SortedList;

/**
 *
 * @author psxvsh
 */
public class RealContentTracesGenerator {

    static String filepath = "F:\\Computer Science\\PhD\\One Simulator\\data\\Youtube Dataset\\";
    static long totalViews = 3708600000L;
    static double averageContentSize = 8.4; // MB
    static double totalLength = 15.2; // years
    static double averageLength = 4.97; // minutes

    private Random random = new Random(0);
    private NavigableMap<Double, HashMap<Integer, Double>> map;

    public RealContentTracesGenerator(int numberOfContents) {
        map = computeMap(numberOfContents);
        /*for (Entry<Double, HashMap<Integer, Double>> entry : map.entrySet()) {
            System.out.printf("%f ", entry.getKey());
        }*/
    }

    private static NavigableMap<Double, HashMap<Integer, Double>> computeMap(
            int numberOfContents) {
        NavigableMap<Double, HashMap<Integer, Double>> map = new TreeMap<>(); // Views (Curriculum %) - ID - Size

        double localTotalViews = 0L;
        try (BufferedReader br = new BufferedReader(new FileReader(filepath + "YoutubeEntDec212006.txt"))) {
            String line;
            int id = 0;
            ArrayList<Double> viewsList = new ArrayList<>();
            ArrayList<Map<Integer, Double>> IDAndSizeList = new ArrayList<>(); // ID - Size
            Map<Integer, Double> tmp;
            while ((line = br.readLine()) != null && id < numberOfContents) {
                double views = Double.valueOf(line.split("\\|")[2]);
                if (views > 100) {
                    viewsList.add(views);
                    String lengthStr = (line.split("\\|"))[1];
                    double length = Double.valueOf(lengthStr.split(":")[0]) * 60 + Double.valueOf(lengthStr.split(":")[1]);
                    tmp = new HashMap();
                    tmp.put(id, (double) (length * averageContentSize / averageLength));
                    IDAndSizeList.add(tmp);
                    localTotalViews += views;
                    id++;
                }
            }
            br.close();
            Collections.sort(viewsList);
            Collections.reverse(viewsList);
            double sum = 0L;
            int count = 0;
            for (Map<Integer, Double> entry : IDAndSizeList) {
                //System.out.println(viewsList.get(count));
                sum += viewsList.get(count);
                map.put((double) (sum / localTotalViews), (HashMap) entry);
                count++;
            }
            //System.out.println(sum);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        return map;
    }

    public int next() {
        double value = random.nextDouble();
        return map.ceilingEntry(value).getValue().entrySet().iterator().next().getKey() + 1;
    }
}

// https://stackoverflow.com/questions/3481828/how-to-split-a-string-in-java
