package vn.drwp.workflow.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.drwp.identity.api.PermissionChecker;
import vn.drwp.identity.api.RoleDirectory;
import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.security.Action;
import vn.drwp.platform.web.security.Actor;
import vn.drwp.record.api.RecordReference;
import vn.drwp.record.api.RecordReferenceReader;
import vn.drwp.workflow.api.WorkflowParticipation;
import vn.drwp.workflow.domain.StateMachine;
import vn.drwp.workflow.domain.TransitionDef;
import vn.drwp.workflow.domain.WorkflowSpec;
import vn.drwp.workflow.infrastructure.WorkflowRepository;
import vn.drwp.workflow.infrastructure.WorkflowRepository.HistoryRow;
import vn.drwp.workflow.infrastructure.WorkflowRepository.InstanceRow;
import vn.drwp.workflow.infrastructure.WorkflowRepository.TaskRow;

/** Hộp việc, chi tiết task/instance. Người không có quyền nhận 404 (chống BOLA). */
@Service
public class WorkflowQueryService implements WorkflowParticipation {

    public record TaskView(UUID id, String name, String stateCode, String status, String assigneeType, String assigneeValue,
                           Instant dueAt, Instant createdAt, String completedBy, String completedAction, String comment,
                           UUID instanceId, UUID recordId, String recordCode, String definitionCode) {
    }

    public record TransitionOption(String code, String label, String to, Action permission, boolean commentRequired,
                                   boolean permitted) {
    }

    public record TaskDetail(TaskView task, long instanceVersion, Map<String, Object> recordData, String recordStatus,
                             boolean assignedToMe, List<TransitionOption> transitions, List<HistoryRow> history) {
    }

    public record InstanceView(UUID id, String definitionCode, int versionNo, UUID recordId, String recordCode,
                               String recordOwner, String status, String currentState, String outcome, String startedBy,
                               Instant startedAt, Instant endedAt, long version, List<TaskView> tasks,
                               List<HistoryRow> history) {
    }

    public record InboxPage(List<TaskView> items, int page, int size, long total) {
    }

    private final WorkflowRepository repository;
    private final SpecCache specs;
    private final RecordReferenceReader records;
    private final PermissionChecker permissions;
    private final RoleDirectory roles;
    private final WorkflowRuntimeService runtime;

    WorkflowQueryService(WorkflowRepository repository, SpecCache specs, RecordReferenceReader records,
                         PermissionChecker permissions, RoleDirectory roles, WorkflowRuntimeService runtime) {
        this.repository = repository;
        this.specs = specs;
        this.records = records;
        this.permissions = permissions;
        this.roles = roles;
        this.runtime = runtime;
    }

    @Transactional(readOnly = true)
    public InboxPage inbox(Actor actor, int page, int size) {
        Set<String> myRoles = roles.rolesOf(actor.subject());
        List<TaskView> items = repository.inbox(actor.subject(), myRoles, size, (long) page * size).stream()
                .map(t -> view(t, repository.instance(t.instanceId(), false).orElseThrow()))
                .toList();
        return new InboxPage(items, page, size, repository.inboxCount(actor.subject(), myRoles));
    }

    @Transactional(readOnly = true)
    public TaskDetail task(Actor actor, UUID taskId) {
        TaskRow task = repository.task(taskId, false).orElseThrow(() -> new NotFoundException("Task", taskId));
        InstanceRow instance = repository.instance(task.instanceId(), false).orElseThrow();
        boolean assigned = "OPEN".equals(task.status()) && runtime.isAssignee(actor.subject(), task);
        if (!assigned && !canView(actor.subject(), instance)) {
            throw new NotFoundException("Task", taskId);
        }
        RecordReference record = records.find(instance.recordId()).orElseThrow();
        List<TransitionOption> options = List.of();
        if (assigned) {
            WorkflowSpec spec = specs.get(instance.versionId());
            String resource = WorkflowRuntimeService.resource(instance.definitionCode());
            options = StateMachine.available(spec, task.stateCode(), record.data()).stream()
                    .map(t -> option(t, permissions.isAllowed(actor.subject(), resource, t.permission())))
                    .toList();
        }
        return new TaskDetail(view(task, instance), instance.version(), record.data(), record.status().name(), assigned,
                options, repository.history(instance.id()));
    }

    @Transactional(readOnly = true)
    public InstanceView instance(Actor actor, UUID instanceId) {
        InstanceRow instance = repository.instance(instanceId, false)
                .orElseThrow(() -> new NotFoundException("Workflow instance", instanceId));
        if (!canView(actor.subject(), instance)) {
            throw new NotFoundException("Workflow instance", instanceId);
        }
        return instanceView(instance);
    }

    @Transactional(readOnly = true)
    public List<InstanceView> instancesOfRecord(Actor actor, UUID recordId) {
        return repository.instancesOfRecord(recordId).stream()
                .filter(i -> canView(actor.subject(), i))
                .map(this::instanceView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isParticipant(UUID recordId, String subject) {
        if (repository.isUserParticipant(recordId, subject)) {
            return true;
        }
        Set<String> myRoles = roles.rolesOf(subject);
        return repository.roleAssigneesOfRecord(recordId).stream().anyMatch(myRoles::contains);
    }

    private boolean canView(String subject, InstanceRow instance) {
        return instance.recordOwner().equals(subject)
                || permissions.isAllowed(subject, WorkflowRuntimeService.resource(instance.definitionCode()), Action.VIEW)
                || isParticipant(instance.recordId(), subject);
    }

    private InstanceView instanceView(InstanceRow i) {
        List<TaskView> tasks = repository.tasksOfInstance(i.id()).stream().map(t -> view(t, i)).toList();
        return new InstanceView(i.id(), i.definitionCode(), i.versionNo(), i.recordId(), i.recordCode(), i.recordOwner(),
                i.status(), i.currentState(), i.outcome(), i.startedBy(), i.startedAt(), i.endedAt(), i.version(), tasks,
                repository.history(i.id()));
    }

    private static TransitionOption option(TransitionDef t, boolean permitted) {
        return new TransitionOption(t.code(), t.label(), t.to(), t.permission(), t.commentRequired(), permitted);
    }

    private static TaskView view(TaskRow t, InstanceRow i) {
        return new TaskView(t.id(), t.name(), t.stateCode(), t.status(), t.assigneeType(), t.assigneeValue(), t.dueAt(),
                t.createdAt(), t.completedBy(), t.completedAction(), t.comment(), i.id(), i.recordId(), i.recordCode(),
                i.definitionCode());
    }
}
