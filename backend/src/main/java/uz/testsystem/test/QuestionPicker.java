package uz.testsystem.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.ToIntFunction;

/**
 * Tanlangan mavzulardan savollarni tasodifiy tanlaydi.
 * <p>
 * Savollar mavzular bo'yicha imkon qadar teng taqsimlanadi: mavzulardan navbatma-navbat
 * bittadan savol olinadi. Biror mavzuda savol tugasa, qolgan o'rinlar boshqa mavzular
 * hisobiga to'ldiriladi. Natija yana aralashtiriladi.
 */
public final class QuestionPicker {

    private QuestionPicker() {
    }

    /**
     * Savollar sonini guruhlar (masalan sinflar) orasida teng taqsimlaydi: navbatma-navbat bittadan, savoli
     * tugagan guruh o'tkazib yuboriladi. Natija: [kalit -> nechta savol olinadi].
     */
    public static <K> Map<K, Integer> allocate(Map<K, Integer> available, int count, Random random) {
        int total = available.values().stream().mapToInt(Integer::intValue).sum();
        if (count < 0 || count > total) {
            throw new IllegalArgumentException("count must be between 0 and " + total);
        }
        List<K> keys = new ArrayList<>(available.keySet());
        // count kalitlar soniga bo'linmasa, ortiqcha savol har safar boshqa guruhga tushadi
        Collections.shuffle(keys, random);
        Map<K, Integer> result = new LinkedHashMap<>();
        available.keySet().forEach(k -> result.put(k, 0));
        int given = 0;
        while (given < count) {
            for (K key : keys) {
                if (given == count) {
                    break;
                }
                if (result.get(key) < available.get(key)) {
                    result.merge(key, 1, Integer::sum);
                    given++;
                }
            }
        }
        return result;
    }

    public static List<Long> pick(Map<Long, List<Long>> questionIdsByTopic, int count, Random random) {
        return pick(questionIdsByTopic, count, random, id -> 0);
    }

    /**
     * {@code priority} – mavzu ichida qaysi savollar birinchi olinadi (kichigi oldin, tenglari orasida tasodifiy):
     * masalan yangi va takrorlash vaqti kelgan savollar, o'quvchi darajasiga mos qiyinlikdagilar.
     * Mavzular orasidagi teng taqsimot o'zgarmaydi.
     */
    public static List<Long> pick(Map<Long, List<Long>> questionIdsByTopic, int count, Random random,
                                  ToIntFunction<Long> priority) {
        int available = questionIdsByTopic.values().stream().mapToInt(List::size).sum();
        if (count < 1 || count > available) {
            throw new IllegalArgumentException("count must be between 1 and " + available);
        }

        List<Deque<Long>> pools = new ArrayList<>();
        for (List<Long> ids : questionIdsByTopic.values()) {
            if (ids.isEmpty()) {
                continue;
            }
            List<Long> shuffled = new ArrayList<>(ids);
            Collections.shuffle(shuffled, random);
            shuffled.sort(Comparator.comparingInt(priority)); // barqaror: teng ustuvorlikda tasodifiy tartib qoladi
            pools.add(new ArrayDeque<>(shuffled));
        }
        // Mavzular tartibi ham tasodifiy: count mavzular soniga bo'linmasa, ortiqcha savol har safar boshqa mavzuga tushadi
        Collections.shuffle(pools, random);

        List<Long> result = new ArrayList<>(count);
        while (result.size() < count) {
            for (Deque<Long> pool : pools) {
                if (result.size() == count) {
                    break;
                }
                Long id = pool.poll();
                if (id != null) {
                    result.add(id);
                }
            }
        }
        Collections.shuffle(result, random);
        return result;
    }
}
