package vn.drwp.audit.infrastructure;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.drwp.audit.application.AuditService;
import vn.drwp.audit.infrastructure.AuditRepository.AuditRow;
import vn.drwp.audit.infrastructure.AuditRepository.Filter;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.web.PageResponse;
import vn.drwp.platform.web.security.CurrentActor;

@RestController
@RequestMapping("/api/audit")
@ConditionalOnRuntimeRole(RuntimeRole.API)
class AuditController {

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/events")
    PageResponse<AuditRow> events(@RequestParam(required = false) String resourceType,
                                  @RequestParam(required = false) String resourceId,
                                  @RequestParam(required = false) String actor,
                                  @RequestParam(required = false) String action,
                                  @RequestParam(required = false) String outcome,
                                  @RequestParam(required = false) Instant from,
                                  @RequestParam(required = false) Instant to,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        var p = audit.search(CurrentActor.require(), new Filter(resourceType, resourceId, actor, action, outcome, from, to),
                PageResponse.clampPage(page), PageResponse.clampSize(size));
        return new PageResponse<>(p.items(), p.page(), p.size(), p.total());
    }
}
