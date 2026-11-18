package vn.drwp.workflow.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import jakarta.validation.constraints.NotNull;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.web.PageResponse;
import vn.drwp.platform.web.security.CurrentActor;
import vn.drwp.workflow.application.WorkflowDefinitionService;
import vn.drwp.workflow.application.WorkflowDefinitionService.DefinitionView;
import vn.drwp.workflow.application.WorkflowDefinitionService.VersionView;
import vn.drwp.workflow.application.WorkflowQueryService;
import vn.drwp.workflow.application.WorkflowQueryService.InboxPage;
import vn.drwp.workflow.application.WorkflowQueryService.InstanceView;
import vn.drwp.workflow.application.WorkflowQueryService.TaskDetail;
import vn.drwp.workflow.application.WorkflowRuntimeService;

@RestController
@RequestMapping("/api/workflow")
@ConditionalOnRuntimeRole(RuntimeRole.API)
class WorkflowController {

    private final WorkflowDefinitionService definitions;
    private final WorkflowRuntimeService runtime;
    private final WorkflowQueryService queries;

    WorkflowController(WorkflowDefinitionService definitions, WorkflowRuntimeService runtime, WorkflowQueryService queries) {
        this.definitions = definitions;
        this.runtime = runtime;
        this.queries = queries;
    }

    record CreateDefinitionRequest(@NotBlank String code, @NotBlank String name, String description,
                                   @NotBlank String formCode, @NotNull Map<String, Object> spec) {
    }

    record UpdateDefinitionRequest(String name, String description, String status) {
    }

    record SpecRequest(Map<String, Object> spec) {
    }

    record TransitionRequest(String comment, Long expectedVersion) {
    }

    record CancelRequest(String reason) {
    }

    // ---------------- definitions
    @GetMapping("/definitions")
    List<DefinitionView> definitions() {
        return definitions.list(CurrentActor.require());
    }

    @PostMapping("/definitions")
    @ResponseStatus(HttpStatus.CREATED)
    VersionView create(@Valid @RequestBody CreateDefinitionRequest r) {
        return definitions.create(CurrentActor.require(), r.code(), r.name(), r.description(), r.formCode(), r.spec());
    }

    @GetMapping("/definitions/{code}")
    DefinitionView definition(@PathVariable String code) {
        return definitions.get(CurrentActor.require(), code);
    }

    @PatchMapping("/definitions/{code}")
    DefinitionView update(@PathVariable String code, @RequestBody UpdateDefinitionRequest r) {
        return definitions.update(CurrentActor.require(), code, r.name(), r.description(), r.status());
    }

    @GetMapping("/definitions/{code}/versions")
    List<VersionView> versions(@PathVariable String code) {
        return definitions.versions(CurrentActor.require(), code);
    }

    @PostMapping("/definitions/{code}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    VersionView newDraft(@PathVariable String code, @RequestBody(required = false) SpecRequest r) {
        return definitions.newDraft(CurrentActor.require(), code, r == null ? null : r.spec());
    }

    @GetMapping("/definitions/{code}/versions/{versionNo}")
    VersionView version(@PathVariable String code, @PathVariable int versionNo) {
        return definitions.version(CurrentActor.require(), code, versionNo);
    }

    @PutMapping("/definitions/{code}/versions/{versionNo}")
    VersionView updateDraft(@PathVariable String code, @PathVariable int versionNo, @RequestBody SpecRequest r) {
        return definitions.updateDraft(CurrentActor.require(), code, versionNo, r.spec());
    }

    @PostMapping("/definitions/{code}/versions/{versionNo}/publish")
    VersionView publish(@PathVariable String code, @PathVariable int versionNo) {
        return definitions.publish(CurrentActor.require(), code, versionNo);
    }

    @PostMapping("/definitions/{code}/versions/{versionNo}/deprecate")
    VersionView deprecate(@PathVariable String code, @PathVariable int versionNo) {
        return definitions.deprecate(CurrentActor.require(), code, versionNo);
    }

    // ---------------- tasks
    @GetMapping("/tasks")
    PageResponse<WorkflowQueryService.TaskView> inbox(@RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        InboxPage p = queries.inbox(CurrentActor.require(), PageResponse.clampPage(page), PageResponse.clampSize(size));
        return new PageResponse<>(p.items(), p.page(), p.size(), p.total());
    }

    @GetMapping("/tasks/{taskId}")
    TaskDetail task(@PathVariable UUID taskId) {
        return queries.task(CurrentActor.require(), taskId);
    }

    @PostMapping("/tasks/{taskId}/transitions/{transition}")
    TaskDetail transition(@PathVariable UUID taskId, @PathVariable String transition,
                          @RequestBody(required = false) TransitionRequest r) {
        runtime.executeTransition(CurrentActor.require(), taskId, transition, r == null ? null : r.comment(),
                r == null ? null : r.expectedVersion());
        return queries.task(CurrentActor.require(), taskId);
    }

    // ---------------- instances
    @GetMapping("/instances/{instanceId}")
    InstanceView instance(@PathVariable UUID instanceId) {
        return queries.instance(CurrentActor.require(), instanceId);
    }

    @GetMapping("/instances")
    List<InstanceView> instancesOfRecord(@RequestParam UUID recordId) {
        return queries.instancesOfRecord(CurrentActor.require(), recordId);
    }

    @PostMapping("/instances/{instanceId}/cancel")
    InstanceView cancel(@PathVariable UUID instanceId, @RequestBody(required = false) CancelRequest r) {
        runtime.cancel(CurrentActor.require(), instanceId, r == null ? null : r.reason());
        return queries.instance(CurrentActor.require(), instanceId);
    }
}
