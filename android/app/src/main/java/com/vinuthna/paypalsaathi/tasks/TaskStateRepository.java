package com.vinuthna.paypalsaathi.tasks;

import com.vinuthna.paypalsaathi.detection.ScreenCategories;

import java.util.ArrayList;
import java.util.List;

public final class TaskStateRepository {
    public enum RestoreStatus {
        NONE,
        RESTORED,
        EXPIRED,
        INVALID
    }

    public interface Storage {
        String read();
        void write(String value);
        void clear();
    }

    private static final String VERSION = "1";
    private static final long RETENTION_MS = 30L * 60L * 1000L;
    private static final long FUTURE_SKEW_MS = 30_000L;
    private final Storage storage;
    private RestoreStatus lastRestoreStatus = RestoreStatus.NONE;

    public TaskStateRepository(Storage storage) {
        this.storage = storage;
    }

    public void save(TaskState state) {
        if (state == null
                || state.goal() == null
                || state.status() == TaskState.Status.IDLE
                || isTerminal(state.status())) {
            clear();
            return;
        }
        storage.write(encode(state));
    }

    public TaskState restore(long now) {
        lastRestoreStatus = RestoreStatus.NONE;
        String value = storage.read();
        if (value == null) {
            return null;
        }
        if (value.length() > 2_048) {
            lastRestoreStatus = RestoreStatus.INVALID;
            clear();
            return null;
        }
        TaskState state = decode(value);
        if (state == null
                || state.goal() == null
                || state.createdAt() > now + FUTURE_SKEW_MS) {
            lastRestoreStatus = RestoreStatus.INVALID;
            clear();
            return null;
        }
        if (isTerminal(state.status())) {
            clear();
            return null;
        }
        if (now - state.createdAt() > RETENTION_MS) {
            lastRestoreStatus = RestoreStatus.EXPIRED;
            clear();
            return null;
        }
        TaskState restored = state.restoredPaused(now);
        save(restored);
        lastRestoreStatus = RestoreStatus.RESTORED;
        return restored;
    }

    public RestoreStatus lastRestoreStatus() {
        return lastRestoreStatus;
    }

    public void clear() {
        storage.clear();
    }

    private static String encode(TaskState state) {
        TaskGoal goal = state.goal();
        return String.join("|",
                VERSION,
                goal.type().name(),
                goal.taskId(),
                Long.toString(goal.createdAt()),
                Integer.toString(goal.confidence()),
                Boolean.toString(goal.clarificationRequired()),
                state.status().name(),
                field(state.currentStepId()),
                join(state.completedStepIds()),
                field(state.expectedNextScreen()),
                field(state.lastObservedScreenIdentifier()),
                state.lastObservedCategory(),
                Long.toString(state.lastObservationTimestamp()),
                state.currentStepStatus() == null
                        ? "-" : state.currentStepStatus().name(),
                Integer.toString(state.retryCount()),
                field(state.pendingClarification()),
                state.lastTransitionReason(),
                Long.toString(state.createdAt()),
                Long.toString(state.updatedAt()));
    }

    private static TaskState decode(String encoded) {
        try {
            String[] fields = encoded.split("\\|", -1);
            if (fields.length != 19 || !VERSION.equals(fields[0])) {
                return null;
            }
            TaskGoal.Type goalType = TaskGoal.Type.valueOf(fields[1]);
            TaskState.Status status = TaskState.Status.valueOf(fields[6]);
            TaskStep.Status stepStatus = "-".equals(fields[13])
                    ? null : TaskStep.Status.valueOf(fields[13]);
            long goalCreatedAt = Long.parseLong(fields[3]);
            int confidence = Integer.parseInt(fields[4]);
            long observationAt = Long.parseLong(fields[12]);
            int retries = Integer.parseInt(fields[14]);
            long createdAt = Long.parseLong(fields[17]);
            long updatedAt = Long.parseLong(fields[18]);
            List<String> completedStepIds = parseList(fields[8]);
            if (!ScreenCategories.isKnownCategory(fields[11])
                    || !fields[2].matches(
                            "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                    || !("true".equals(fields[5]) || "false".equals(fields[5]))
                    || goalCreatedAt < 0
                    || observationAt < 0
                    || createdAt < 0
                    || updatedAt < 0
                    || completedStepIds == null) {
                return null;
            }
            TaskGoal goal = new TaskGoal(
                    fields[2],
                    goalType,
                    goalCreatedAt,
                    confidence,
                    Boolean.parseBoolean(fields[5]));
            return new TaskState(
                    goal,
                    status,
                    nullable(fields[7]),
                    completedStepIds,
                    nullable(fields[9]),
                    nullable(fields[10]),
                    fields[11],
                    observationAt,
                    stepStatus,
                    retries,
                    nullable(fields[15]),
                    fields[16],
                    createdAt,
                    updatedAt);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private static String field(String value) {
        return value == null ? "-" : value;
    }

    private static String nullable(String value) {
        return "-".equals(value) ? null : value;
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty()
                ? "-"
                : String.join(",", values);
    }

    private static List<String> parseList(String value) {
        List<String> result = new ArrayList<>();
        if ("-".equals(value) || value.isEmpty()) {
            return result;
        }
        for (String item : value.split(",")) {
            if (!item.matches("[a-z_]{1,48}")) {
                return null;
            }
            result.add(item);
        }
        return result;
    }

    private static boolean isTerminal(TaskState.Status status) {
        return status == TaskState.Status.COMPLETED
                || status == TaskState.Status.CANCELLED
                || status == TaskState.Status.FAILED;
    }
}
