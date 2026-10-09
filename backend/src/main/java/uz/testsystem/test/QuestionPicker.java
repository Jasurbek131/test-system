package uz.testsystem.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;

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

    public static List<Long> pick(Map<Long, List<Long>> questionIdsByTopic, int count, Random random) {
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
