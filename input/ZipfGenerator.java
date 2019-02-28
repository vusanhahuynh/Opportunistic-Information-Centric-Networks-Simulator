/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package input;

import java.util.Random;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 *
 * @author psxvsh
 */
public class ZipfGenerator {

    private Random random = new Random(0);
    private NavigableMap<Double, Integer> map;

    public ZipfGenerator(int size, double skew) {
        map = computeMap(size, skew);
    }

    private static NavigableMap<Double, Integer> computeMap(
            int size, double skew) {
        NavigableMap<Double, Integer> map
                = new TreeMap<Double, Integer>();

        double div = 0;
        for (int i = 1; i <= size; i++) {
            div += (1 / Math.pow(i, skew));
        }

        double sum = 0;
        for (int i = 1; i <= size; i++) {
            double p = (1.0d / Math.pow(i, skew)) / div;
            sum += p;
            map.put(sum, i - 1);
        }
        return map;
    }

    public int next() {
        double value = random.nextDouble();
        return map.ceilingEntry(value).getValue() + 1;
    }
}
