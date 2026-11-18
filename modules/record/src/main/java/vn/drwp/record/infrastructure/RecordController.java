package vn.drwp.record.infrastructure;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.web.PageResponse;
import vn.drwp.platform.web.security.CurrentActor;
import vn.drwp.record.api.RecordStatus;
import vn.drwp.record.application.RecordService;
import vn.drwp.record.application.RecordService.PageView;
import vn.drwp.record.application.RecordService.RecordView;
import vn.drwp.record.domain.RecordQuery;

@RestController
@RequestMapping("/api/records")
@ConditionalOnRuntimeRole(RuntimeRole.API)
class RecordController {

    private static final String DATA_PREFIX = "data.";

    private final RecordService records;

    RecordController(RecordService records) {
        this.records = records;
    }

    record CreateRecordRequest(@NotBlank String formCode, Map<String, Object> data, boolean submit) {
    }

    record UpdateRecordRequest(Map<String, Object> data, Long expectedVersion) {
    }

    record SubmitRequest(Long expectedVersion) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RecordView create(@Valid @RequestBody CreateRecordRequest r) {
        return records.create(CurrentActor.require(), r.formCode(), r.data(), r.submit());
    }

    @GetMapping("/{id}")
    RecordView get(@PathVariable UUID id) {
        return records.get(CurrentActor.require(), id);
    }

    @PutMapping("/{id}")
    RecordView update(@PathVariable UUID id, @RequestBody UpdateRecordRequest r) {
        return records.update(CurrentActor.require(), id, r.data(), r.expectedVersion());
    }

    @PostMapping("/{id}/submit")
    RecordView submit(@PathVariable UUID id, @RequestBody(required = false) SubmitRequest r) {
        return records.submit(CurrentActor.require(), id, r == null ? null : r.expectedVersion());
    }

    @PostMapping("/{id}/cancel")
    RecordView cancel(@PathVariable UUID id) {
        return records.cancel(CurrentActor.require(), id);
    }

    @GetMapping("/{id}/revisions")
    List<RecordRepository.Revision> revisions(@PathVariable UUID id) {
        return records.revisions(CurrentActor.require(), id);
    }

    /**
     * Tìm kiếm. Lọc trường động bằng tham số {@code data.<field>=<value>} (chỉ trường {@code filterable}).
     * Ví dụ: {@code GET /api/records?formCode=LEAVE_REQUEST&status=IN_PROGRESS&data.leaveType=ANNUAL}
     */
    @GetMapping
    PageResponse<RecordView> search(@RequestParam(required = false) String formCode,
                                    @RequestParam(required = false) RecordStatus status,
                                    @RequestParam(required = false) String createdBy,
                                    @RequestParam(required = false) String recordCode,
                                    @RequestParam(required = false) Instant createdFrom,
                                    @RequestParam(required = false) Instant createdTo,
                                    @RequestParam(required = false) RecordQuery.Sort sort,
                                    @RequestParam(defaultValue = "true") boolean desc,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size,
                                    @RequestParam Map<String, String> allParams) {
        Map<String, String> dataFilters = new HashMap<>();
        allParams.forEach((k, v) -> {
            if (k.startsWith(DATA_PREFIX)) {
                dataFilters.put(k.substring(DATA_PREFIX.length()), v);
            }
        });
        int p = PageResponse.clampPage(page);
        int s = PageResponse.clampSize(size);
        PageView result = records.search(CurrentActor.require(), new RecordQuery(formCode, status, createdBy, recordCode,
                createdFrom, createdTo, dataFilters, sort, desc, p, s));
        return new PageResponse<>(result.items(), result.page(), result.size(), result.total());
    }
}
