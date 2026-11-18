package vn.drwp.platform.web;

import java.util.List;
import java.util.function.Function;

/** Trang kết quả ổn định cho API. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static final int MAX_SIZE = 100;

    public static int clampSize(Integer size) {
        if (size == null || size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_SIZE);
    }

    public static int clampPage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(items.stream().map(mapper).toList(), page, size, total);
    }
}
