package vn.drwp.platform.kernel.security;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Bảng action chuẩn (tài liệu 01 mục 7). Bit cố định, KHÔNG được đổi số bit của action đã phát hành
 * (thay đổi phải qua ADR theo tài liệu 02 mục 10).
 */
public enum Action {
    VIEW(0), CREATE(1), UPDATE(2), DELETE(3), APPROVE(4), REJECT(5),
    EXPORT(6), ASSIGN(7), SHARE(8), CONFIGURE(9), AUDIT(10), MANAGE(11);

    private final int bit;

    Action(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public long mask() {
        return 1L << bit;
    }

    public static Action parse(String name) {
        return Action.valueOf(name.trim().toUpperCase(Locale.ROOT));
    }

    public static Set<Action> fromMask(long mask) {
        EnumSet<Action> result = EnumSet.noneOf(Action.class);
        for (Action a : values()) {
            if ((mask & a.mask()) != 0) {
                result.add(a);
            }
        }
        return result;
    }
}
