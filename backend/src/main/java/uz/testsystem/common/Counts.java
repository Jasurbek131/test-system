package uz.testsystem.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Counts {

    private Counts() {
    }

    /** [id, count] qatorlarini Map ga aylantiradi. */
    public static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }
}
